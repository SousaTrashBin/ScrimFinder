package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.client.RankingServiceClient;
import fc.ul.scrimfinder.domain.*;
import fc.ul.scrimfinder.domain.Queue;
import fc.ul.scrimfinder.dto.external.PlayerRankingDTO;
import fc.ul.scrimfinder.dto.request.JoinQueueRequest;
import fc.ul.scrimfinder.dto.request.MatchResultRequest;
import fc.ul.scrimfinder.dto.response.LobbyDTO;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.dto.response.MatchTicketDTO;
import fc.ul.scrimfinder.exception.LeagueAccountNotLinkedException;
import fc.ul.scrimfinder.exception.PlayerNotFoundException;
import fc.ul.scrimfinder.exception.QueueNotFoundException;
import fc.ul.scrimfinder.exception.TicketNotFoundException;
import fc.ul.scrimfinder.grpc.InitializePlayerMMRRequest;
import fc.ul.scrimfinder.mapper.LobbyMapper;
import fc.ul.scrimfinder.mapper.MatchMapper;
import fc.ul.scrimfinder.mapper.MatchTicketMapper;
import fc.ul.scrimfinder.repository.*;
import fc.ul.scrimfinder.service.MatchmakingService;
import fc.ul.scrimfinder.util.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Slf4j
@ApplicationScoped
public class MatchmakingServiceImpl implements MatchmakingService {

    private static final int K_FACTOR = 40;
    @Inject PlayerRepository playerRepository;

    @Inject ReadOnlyPlayerRepository readOnlyPlayerRepository;
    @Inject ReplicaMatchmakingReadRepository replicaReadRepository;

    @Inject QueueRepository queueRepository;
    @Inject MatchTicketRepository ticketRepository;

    @Inject ReadOnlyMatchTicketRepository readOnlyTicketRepository;

    @Inject LobbyRepository lobbyRepository;
    @Inject MatchRepository matchRepository;

    @Inject ReadOnlyMatchRepository readOnlyMatchRepository;

    @Inject RedisMatchmakingRepository redisRepository;
    @Inject DistributedLockService lockService;
    @Inject MatchTicketMapper ticketMapper;
    @Inject LobbyMapper lobbyMapper;
    @Inject MatchMapper matchMapper;
    @Inject @RestClient RankingServiceClient rankingServiceClient;

    @Inject
    @io.quarkus.grpc.GrpcClient("ranking-service")
    fc.ul.scrimfinder.grpc.RankingService rankingGrpcClient;

    @Inject MatchFailureStateService matchFailureStateService;
    @Inject MatchResultSyncSagaService matchResultSyncSagaService;
    @Inject TransactionSynchronizationRegistry txSyncRegistry;

    @Override
    @Transactional
    @Retry(maxRetries = 2, delay = 500)
    @Timeout(3000)
    public MatchTicketDTO joinQueue(JoinQueueRequest request) {
        log.info(
                "\u001B[33m[PENDING]\u001B[0m Player {} attempting to join queue {}",
                request.getPlayerId(),
                request.getQueueId());
        Player player =
                playerRepository
                        .findByIdOptional(request.getPlayerId())
                        .orElseThrow(
                                () -> {
                                    log.error(
                                            "\u001B[31m[ERROR]\u001B[0m Player not found: {}", request.getPlayerId());
                                    return new PlayerNotFoundException("Player not found: " + request.getPlayerId());
                                });

        Queue queue =
                queueRepository
                        .findByIdOptional(request.getQueueId())
                        .orElseThrow(
                                () -> {
                                    log.error("\u001B[31m[ERROR]\u001B[0m Queue not found: {}", request.getQueueId());
                                    return new QueueNotFoundException("Queue not found: " + request.getQueueId());
                                });

        List<PlayerRankingDTO> rankings = null;
        try {
            rankings = rankingServiceClient.getPlayerRanking(player.getId(), queue.getId());
        } catch (LeagueAccountNotLinkedException e) {
            log.info(
                    "\u001B[34m[INFO]\u001B[0m Queue ranking missing for player {} in queue {}. Trying bootstrap.",
                    player.getId(),
                    queue.getId());
        }

        if (rankings == null || rankings.isEmpty()) {
            log.info(
                    "\u001B[34m[INFO]\u001B[0m Missing queue ranking for player {} in queue {}. Bootstrapping ranking.",
                    player.getId(),
                    queue.getId());
            try {
                var grpcResponse =
                        rankingGrpcClient
                                .initializePlayerMMR(
                                        InitializePlayerMMRRequest.newBuilder()
                                                .setPlayerId(player.getId().toString())
                                                .setQueueId(queue.getId().toString())
                                                .build())
                                .await()
                                .indefinitely();
                if (!grpcResponse.getSuccess()) {
                    log.warn(
                            "\u001B[33m[WARN]\u001B[0m Queue ranking bootstrap RPC returned failure for player {}: {}",
                            player.getId(),
                            grpcResponse.getMessage());
                }
            } catch (RuntimeException e) {
                log.warn(
                        "\u001B[33m[WARN]\u001B[0m Queue ranking bootstrap call failed for player {}: {}. Re-checking ranking.",
                        player.getId(),
                        e.getMessage());
            }
            rankings = rankingServiceClient.getPlayerRanking(player.getId(), queue.getId());
            if (rankings == null || rankings.isEmpty()) {
                log.error(
                        "\u001B[31m[ERROR]\u001B[0m Queue ranking bootstrap failed for player {}",
                        player.getId());
                throw new LeagueAccountNotLinkedException(
                        "A valid League Account with a region must be linked before joining the queue.");
            }
        }

        PlayerRankingDTO ranking = rankings.get(0);

        if (ranking.lolAccountPPUID() == null || ranking.region() == null) {
            log.error("\u001B[31m[ERROR]\u001B[0m Invalid ranking data for player {}", player.getId());
            throw new LeagueAccountNotLinkedException(
                    "A valid League Account with a region must be linked before joining the queue.");
        }

        if (queue.getRegion() != null && queue.getRegion() != ranking.region()) {
            log.warn(
                    "\u001B[33m[WARN]\u001B[0m Region mismatch: Queue requires {} but player is {}",
                    queue.getRegion(),
                    ranking.region());
            throw new RuntimeException("This queue is restricted to region: " + queue.getRegion());
        }

        MatchTicket ticket = new MatchTicket();
        ticket.setPlayer(player);
        ticket.setQueue(queue);
        ticket.setRegion(ranking.region());
        ticket.setRole(request.getRole() != null ? request.getRole() : Role.NONE);
        ticket.setStatus(TicketStatus.IN_QUEUE);
        ticket.setMmr(ranking.mmr());
        ticket.setRiotPuuid(ranking.lolAccountPPUID());
        ticketRepository.persist(ticket);

        redisRepository.addTicket(
                queue.getId(), ticket.getRegion(), ticket.getRole(), ticket.getId(), ticket.getMmr());

        log.info(
                "\u001B[32m[STATE CHANGE]\u001B[0m Ticket {} created: Player {} in queue {} (Region: {}, Role: {}, MMR: {})",
                ticket.getId(),
                player.getId(),
                queue.getId(),
                ticket.getRegion(),
                ticket.getRole(),
                ticket.getMmr());

        processQueue(queue, ticket.getRegion());

        return ticketMapper.toDTO(ticket);
    }

    private void processQueue(Queue queue, Region region) {
        String lockKey =
                "lock:matchmaking:queue:" + queue.getId().toString() + ":region:" + region.name();
        if (!lockService.acquireLock(lockKey, Duration.ofSeconds(10))) {
            return;
        }

        try {
            if (queue.isRoleQueue()) {
                processRoleQueue(queue, region);
            } else {
                processStandardQueue(queue, region);
            }
        } finally {
            lockService.releaseLock(lockKey);
        }
    }

    private void processStandardQueue(Queue queue, Region region) {
        List<UUID> ticketIds = redisRepository.getTickets(queue.getId(), region, Role.NONE);
        if (ticketIds.size() < queue.getRequiredPlayers()) return;

        List<MatchTicket> tickets =
                ticketIds.stream()
                        .map(id -> ticketRepository.findById(id))
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparingInt(MatchTicket::getMmr))
                        .collect(Collectors.toList());

        if (tickets.size() < queue.getRequiredPlayers()) return;

        if (queue.getMode() == MatchmakingMode.RANK_BASED) {
            for (int i = 0; i <= tickets.size() - queue.getRequiredPlayers(); i++) {
                MatchTicket min = tickets.get(i);
                MatchTicket max = tickets.get(i + queue.getRequiredPlayers() - 1);
                if (max.getMmr() - min.getMmr() <= queue.getMmrWindow()) {
                    List<MatchTicket> matched =
                            new ArrayList<>(tickets.subList(i, i + queue.getRequiredPlayers()));
                    log.info(
                            "\u001B[34m[INFO]\u001B[0m Found standard match within MMR window: {} to {}",
                            min.getMmr(),
                            max.getMmr());
                    createMatchProposal(queue, region, matched);
                    return;
                }
            }
        } else {
            log.info("\u001B[34m[INFO]\u001B[0m Found standard match (ANY mode)");
            createMatchProposal(queue, region, tickets.subList(0, queue.getRequiredPlayers()));
        }
    }

    private void processRoleQueue(Queue queue, Region region) {
        Map<Role, List<UUID>> ticketIdsByRole = new HashMap<>();
        for (Role role : Role.values()) {
            if (role == Role.NONE) continue;
            ticketIdsByRole.put(role, redisRepository.getTickets(queue.getId(), region, role));
        }

        if (hasEnoughForRoleQueueRedis(ticketIdsByRole)) {
            List<MatchTicket> matched = tryFindRoleMatch(queue, region, ticketIdsByRole);
            if (matched != null) {
                createMatchProposal(queue, region, matched);
            }
        }
    }

    private boolean hasEnoughForRoleQueueRedis(Map<Role, List<UUID>> byRole) {
        return byRole.getOrDefault(Role.TOP, List.of()).size() >= 2
                && byRole.getOrDefault(Role.JUNGLE, List.of()).size() >= 2
                && byRole.getOrDefault(Role.MID, List.of()).size() >= 2
                && byRole.getOrDefault(Role.BOTTOM, List.of()).size() >= 2
                && byRole.getOrDefault(Role.SUPPORT, List.of()).size() >= 2;
    }

    private List<MatchTicket> tryFindRoleMatch(
            Queue queue, Region region, Map<Role, List<UUID>> ticketIdsByRole) {
        List<MatchTicket> candidates = new ArrayList<>();
        for (Role role : Role.values()) {
            if (role == Role.NONE) continue;
            candidates.add(ticketRepository.findById(ticketIdsByRole.get(role).get(0)));
            candidates.add(ticketRepository.findById(ticketIdsByRole.get(role).get(1)));
        }

        if (queue.getMode() == MatchmakingMode.RANK_BASED) {
            int minValue = candidates.stream().mapToInt(MatchTicket::getMmr).min().orElse(0);
            int maxValue = candidates.stream().mapToInt(MatchTicket::getMmr).max().orElse(0);
            if (maxValue - minValue <= queue.getMmrWindow()) {
                return candidates;
            }
            return null;
        }
        return candidates;
    }

    @Transactional
    protected void createMatchProposal(Queue queue, Region region, List<MatchTicket> matchedTickets) {
        for (MatchTicket t : matchedTickets) {
            if (!redisRepository.removeTicket(queue.getId(), region, t.getRole(), t.getId())) {
                return;
            }
            registerRollbackCompensation(
                    () ->
                            redisRepository.addTicket(queue.getId(), region, t.getRole(), t.getId(), t.getMmr()));
        }

        Lobby lobby = new Lobby();
        lobby.setQueue(queue);
        lobby.setRegion(region);
        lobbyRepository.persist(lobby);
        if (queue.isRoleQueue()) {
            Map<Role, List<MatchTicket>> byRole =
                    matchedTickets.stream().collect(Collectors.groupingBy(MatchTicket::getRole));
            for (Role role : Role.values()) {
                if (role == Role.NONE) continue;
                List<MatchTicket> tickets = byRole.get(role);
                if (tickets != null && tickets.size() >= 2) {
                    tickets.sort(Comparator.comparingInt(MatchTicket::getMmr));
                    tickets.get(0).setTeam(1);
                    tickets.get(1).setTeam(2);
                }
            }
        } else {
            matchedTickets.sort(Comparator.comparingInt(MatchTicket::getMmr).reversed());
            for (int i = 0; i < matchedTickets.size(); i++) {
                int team = ((i / 2) % 2 == 0) ? (i % 2 + 1) : (2 - (i % 2));
                matchedTickets.get(i).setTeam(team);
            }
        }

        for (MatchTicket t : matchedTickets) {
            t.setStatus(TicketStatus.MATCHED);
            t.setLobby(lobby);
            ticketRepository.persist(t);
        }

        lobby.setTickets(matchedTickets);
        lobbyRepository.persist(lobby);

        Match match = new Match();
        match.setLobby(lobby);
        match.setState(MatchState.PENDING_ACCEPTANCE);
        matchRepository.persist(match);
        matchResultSyncSagaService.recordLifecycle(
                match.getId(),
                "MATCH_PROPOSAL_CREATED",
                "SUCCESS",
                "Match created and waiting for acceptance");

        log.info(
                "\u001B[32m[STATE CHANGE]\u001B[0m Match Proposal Created: Match {} (Lobby {}) for {} tickets",
                match.getId(),
                lobby.getId(),
                matchedTickets.size());
    }

    @Override
    @Transactional
    public MatchDTO acceptMatch(UUID matchId, UUID playerId) {
        log.info("\u001B[33m[PENDING]\u001B[0m Player {} accepting match {}", playerId, matchId);
        Match match =
                matchRepository
                        .findByIdOptional(matchId)
                        .orElseThrow(
                                () -> {
                                    log.error("\u001B[31m[ERROR]\u001B[0m Match {} not found", matchId);
                                    return new RuntimeException("Match not found");
                                });

        if (match.getState() != MatchState.PENDING_ACCEPTANCE) {
            log.warn(
                    "\u001B[33m[WARN]\u001B[0m Player {} tried to accept match {} which is in state {}",
                    playerId,
                    matchId,
                    match.getState());
            throw new RuntimeException("Match is not in acceptance phase");
        }

        boolean belongsToMatch =
                match.getLobby().getTickets().stream()
                        .anyMatch(t -> t.getPlayer().getId().equals(playerId));
        if (!belongsToMatch) {
            log.warn(
                    "\u001B[33m[WARN]\u001B[0m Player {} attempted to accept match {} without a ticket in that lobby",
                    playerId,
                    matchId);
            throw new PlayerNotFoundException("Player is not part of this match");
        }

        match.getAcceptedPlayerIds().add(playerId);
        matchRepository.persist(match);

        if (match.getAcceptedPlayerIds().size() == match.getLobby().getQueue().getRequiredPlayers()) {
            match.setState(MatchState.IN_PROGRESS);
            match.setStartedAt(LocalDateTime.now());
            matchRepository.persist(match);
            matchResultSyncSagaService.recordLifecycle(
                    match.getId(), "MATCH_ACCEPTED", "SUCCESS", "All required players accepted the match");
            log.info("\u001B[32m[STATE CHANGE]\u001B[0m Match {} is now IN_PROGRESS", matchId);
        }

        return matchMapper.toDTO(match);
    }

    @Override
    @Transactional
    public void declineMatch(UUID matchId, UUID playerId) {
        log.info("\u001B[33m[PENDING]\u001B[0m Player {} declining match {}", playerId, matchId);
        Match match =
                matchRepository
                        .findByIdOptional(matchId)
                        .orElseThrow(
                                () -> {
                                    log.error("\u001B[31m[ERROR]\u001B[0m Match {} not found", matchId);
                                    return new RuntimeException("Match not found");
                                });

        match.setState(MatchState.CANCELLED);
        match.setEndedAt(LocalDateTime.now());
        matchResultSyncSagaService.recordLifecycle(
                match.getId(), "MATCH_DECLINED", "SUCCESS", "Match cancelled after player decline");
        log.info(
                "\u001B[32m[STATE CHANGE]\u001B[0m Match {} CANCELLED by player {}", matchId, playerId);

        for (MatchTicket t : match.getLobby().getTickets()) {
            if (t.getPlayer().getId().equals(playerId)) {
                t.setStatus(TicketStatus.CANCELLED);
            } else {
                t.setStatus(TicketStatus.IN_QUEUE);
                t.setLobby(null);
                t.setTeam(null);
                redisRepository.addTicket(
                        match.getLobby().getQueue().getId(), t.getRegion(), t.getRole(), t.getId(), t.getMmr());
                registerRollbackCompensation(
                        () ->
                                redisRepository.removeTicket(
                                        match.getLobby().getQueue().getId(), t.getRegion(), t.getRole(), t.getId()));
            }
            ticketRepository.persist(t);
        }
        matchRepository.persist(match);
    }

    @Override
    @Transactional
    public void linkMatch(UUID matchId, String externalGameId) {
        log.info(
                "\u001B[33m[PENDING]\u001B[0m Linking match {} with external Game ID {}",
                matchId,
                externalGameId);
        Match match =
                matchRepository
                        .findByIdOptional(matchId)
                        .orElseThrow(
                                () -> {
                                    log.error("\u001B[31m[ERROR]\u001B[0m Match {} not found for linking", matchId);
                                    return new RuntimeException("Match not found");
                                });
        match.setExternalGameId(externalGameId);
        matchRepository.persist(match);
        matchResultSyncSagaService.recordLifecycle(
                match.getId(), "MATCH_LINKED", "SUCCESS", "External game id linked: " + externalGameId);
        log.info(
                "\u001B[32m[STATE CHANGE]\u001B[0m Match {} successfully linked to {}",
                matchId,
                externalGameId);
    }

    @Override
    @Transactional
    @Retry(maxRetries = 2, delay = 1000)
    @Timeout(10000)
    @CircuitBreaker(requestVolumeThreshold = 5, failureRatio = 0.6, delay = 5000)
    public void completeMatch(UUID matchId) {
        log.info("\u001B[33m[PENDING]\u001B[0m Completing match {}...", matchId);
        Match match =
                matchRepository
                        .findByIdOptional(matchId)
                        .orElseThrow(
                                () -> {
                                    log.error(
                                            "\u001B[31m[ERROR]\u001B[0m Match {} not found for completion", matchId);
                                    return new RuntimeException("Match not found");
                                });

        if (match.getExternalGameId() == null) {
            log.error("\u001B[31m[ERROR]\u001B[0m Match {} has no external game ID linked", matchId);
            throw new RuntimeException(
                    "Match must be linked with a valid League of Legends Game ID first.");
        }

        if (match.getState() == MatchState.COMPLETED) {
            log.info("\u001B[34m[INFO]\u001B[0m Match {} is already COMPLETED", matchId);
            return;
        }

        log.info(
                "\u001B[34m[INFO]\u001B[0m Calculating MMR deltas for match {} (External ID: {})",
                matchId,
                match.getExternalGameId());

        List<MatchTicket> team1 =
                match.getLobby().getTickets().stream()
                        .filter(t -> t.getTeam() != null && t.getTeam() == 1)
                        .toList();
        List<MatchTicket> team2 =
                match.getLobby().getTickets().stream()
                        .filter(t -> t.getTeam() != null && t.getTeam() == 2)
                        .toList();

        double avgMMR1 = team1.stream().mapToInt(MatchTicket::getMmr).average().orElse(0);
        double avgMMR2 = team2.stream().mapToInt(MatchTicket::getMmr).average().orElse(0);

        Map<String, MatchResultRequest.PlayerDelta> deltas = new HashMap<>();
        for (MatchTicket t : team1) {
            double expected = 1.0 / (1.0 + Math.pow(10.0, (avgMMR2 - t.getMmr()) / 400.0));
            int winDelta = (int) Math.round(K_FACTOR * (1.0 - expected));
            int lossDelta = (int) Math.round(K_FACTOR * expected);
            deltas.put(t.getRiotPuuid(), new MatchResultRequest.PlayerDelta(winDelta, lossDelta));
        }
        for (MatchTicket t : team2) {
            double expected = 1.0 / (1.0 + Math.pow(10.0, (avgMMR1 - t.getMmr()) / 400.0));
            int winDelta = (int) Math.round(K_FACTOR * (1.0 - expected));
            int lossDelta = (int) Math.round(K_FACTOR * expected);
            deltas.put(t.getRiotPuuid(), new MatchResultRequest.PlayerDelta(winDelta, lossDelta));
        }

        log.info("\u001B[34m[INFO]\u001B[0m Enqueuing result sync saga step for match {}", matchId);
        matchResultSyncSagaService.enqueueIfMissing(match, deltas);
        matchResultSyncSagaService.recordLifecycle(
                matchId, "RESULT_SYNC_ENQUEUED", "SUCCESS", "Ranking result sync event persisted");

        try {
            boolean synced = matchResultSyncSagaService.processNow(matchId);
            if (!synced) {
                matchFailureStateService.markResultReportingFailed(matchId);
                matchResultSyncSagaService.recordLifecycle(
                        matchId, "RESULT_SYNC", "RETRY_SCHEDULED", "Initial sync failed, retry is scheduled");
                throw new RuntimeException(
                        "Match results queued for retry. Current state: RESULT_REPORTING_FAILED");
            }
            log.info(
                    "\u001B[32m[SUCCESS]\u001B[0m Match {} successfully COMPLETED and results reported",
                    matchId);
        } catch (Exception e) {
            log.error(
                    "\u001B[31m[ERROR]\u001B[0m Failed to report match results for match {}: {}",
                    matchId,
                    e.getMessage());
            matchFailureStateService.markResultReportingFailed(matchId);
            throw new RuntimeException("Match results failed to sync. State: RESULT_REPORTING_FAILED", e);
        }
    }

    @Override
    @Transactional
    public void leaveQueue(UUID ticketId) {
        log.info("\u001B[33m[PENDING]\u001B[0m Player attempting to leave queue. Ticket: {}", ticketId);
        MatchTicket ticket =
                ticketRepository
                        .findByIdOptional(ticketId)
                        .orElseThrow(
                                () -> {
                                    log.warn(
                                            "\u001B[33m[WARN]\u001B[0m Leave queue failed: Ticket {} not found",
                                            ticketId);
                                    return new TicketNotFoundException("Ticket not found: " + ticketId);
                                });

        if (ticket.getStatus() == TicketStatus.IN_QUEUE) {
            ticket.setStatus(TicketStatus.CANCELLED);
            ticketRepository.persist(ticket);
            redisRepository.removeTicket(
                    ticket.getQueue().getId(), ticket.getRegion(), ticket.getRole(), ticket.getId());
            registerRollbackCompensation(
                    () ->
                            redisRepository.addTicket(
                                    ticket.getQueue().getId(),
                                    ticket.getRegion(),
                                    ticket.getRole(),
                                    ticket.getId(),
                                    ticket.getMmr()));
            log.info(
                    "\u001B[32m[STATE CHANGE]\u001B[0m Ticket {} CANCELLED: Player left queue", ticketId);
        } else {
            log.warn(
                    "\u001B[33m[WARN]\u001B[0m Ticket {} is not in queue (Status: {}). Cannot leave.",
                    ticketId,
                    ticket.getStatus());
        }
    }

    @Override
    public LobbyDTO getLobbyByTicket(UUID ticketId) {
        if (isTransactionActive()) {
            MatchTicket ticket =
                    readOnlyTicketRepository
                            .findByIdOptional(ticketId)
                            .orElseThrow(() -> new TicketNotFoundException("Ticket not found: " + ticketId));
            if (ticket.getLobby() == null) return null;
            return toLobbyDTOWithMatchId(ticket.getLobby());
        }
        try {
            return replicaReadRepository
                    .findLobbyByTicketId(ticketId)
                    .orElseThrow(() -> new TicketNotFoundException("Ticket not found: " + ticketId));
        } catch (TicketNotFoundException e) {
            throw e;
        } catch (Exception replicaFailure) {
            log.warn(
                    "\u001B[33m[WARN]\u001B[0m Replica read failed for lobby by ticket {}, falling back to primary: {}",
                    ticketId,
                    replicaFailure.getMessage());
            MatchTicket ticket =
                    readOnlyTicketRepository
                            .findByIdOptional(ticketId)
                            .orElseThrow(() -> new TicketNotFoundException("Ticket not found: " + ticketId));
            if (ticket.getLobby() == null) return null;
            return toLobbyDTOWithMatchId(ticket.getLobby());
        }
    }

    @Override
    public List<MatchTicketDTO> getTicketsByPlayer(UUID playerId) {
        if (isTransactionActive()) {
            if (readOnlyPlayerRepository.findByIdOptional(playerId).isEmpty()) {
                throw new PlayerNotFoundException("Player not found: " + playerId);
            }
            return readOnlyTicketRepository.findByPlayerId(playerId).stream()
                    .map(ticketMapper::toDTO)
                    .toList();
        }
        try {
            if (!replicaReadRepository.playerExists(playerId)) {
                throw new PlayerNotFoundException("Player not found: " + playerId);
            }
            return replicaReadRepository.findTicketsByPlayerId(playerId);
        } catch (PlayerNotFoundException e) {
            throw e;
        } catch (Exception replicaFailure) {
            log.warn(
                    "\u001B[33m[WARN]\u001B[0m Replica read failed for tickets by player {}, falling back to primary: {}",
                    playerId,
                    replicaFailure.getMessage());
            if (readOnlyPlayerRepository.findByIdOptional(playerId).isEmpty()) {
                throw new PlayerNotFoundException("Player not found: " + playerId);
            }
            return readOnlyTicketRepository.findByPlayerId(playerId).stream()
                    .map(ticketMapper::toDTO)
                    .toList();
        }
    }

    @Override
    public List<LobbyDTO> getLobbiesByPlayer(UUID playerId) {
        if (isTransactionActive()) {
            if (readOnlyPlayerRepository.findByIdOptional(playerId).isEmpty()) {
                throw new PlayerNotFoundException("Player not found: " + playerId);
            }
            return readOnlyTicketRepository.findByPlayerId(playerId).stream()
                    .map(MatchTicket::getLobby)
                    .filter(Objects::nonNull)
                    .distinct()
                    .map(this::toLobbyDTOWithMatchId)
                    .toList();
        }
        try {
            if (!replicaReadRepository.playerExists(playerId)) {
                throw new PlayerNotFoundException("Player not found: " + playerId);
            }
            return replicaReadRepository.findLobbiesByPlayerId(playerId);
        } catch (PlayerNotFoundException e) {
            throw e;
        } catch (Exception replicaFailure) {
            log.warn(
                    "\u001B[33m[WARN]\u001B[0m Replica read failed for lobbies by player {}, falling back to primary: {}",
                    playerId,
                    replicaFailure.getMessage());
            if (readOnlyPlayerRepository.findByIdOptional(playerId).isEmpty()) {
                throw new PlayerNotFoundException("Player not found: " + playerId);
            }

            return readOnlyTicketRepository.findByPlayerId(playerId).stream()
                    .map(MatchTicket::getLobby)
                    .filter(Objects::nonNull)
                    .distinct()
                    .map(this::toLobbyDTOWithMatchId)
                    .toList();
        }
    }

    private LobbyDTO toLobbyDTOWithMatchId(Lobby lobby) {
        LobbyDTO lobbyDTO = lobbyMapper.toDTO(lobby);

        readOnlyMatchRepository
                .find("lobby", lobby)
                .firstResultOptional()
                .ifPresent(match -> lobbyDTO.setMatchId(match.getId()));

        return lobbyDTO;
    }

    private void registerRollbackCompensation(Runnable compensation) {
        txSyncRegistry.registerInterposedSynchronization(
                new Synchronization() {
                    @Override
                    public void beforeCompletion() {}

                    @Override
                    public void afterCompletion(int status) {
                        if (status != Status.STATUS_COMMITTED) {
                            compensation.run();
                        }
                    }
                });
    }

    private boolean isTransactionActive() {
        int status = txSyncRegistry.getTransactionStatus();
        return status == Status.STATUS_ACTIVE || status == Status.STATUS_MARKED_ROLLBACK;
    }
}
