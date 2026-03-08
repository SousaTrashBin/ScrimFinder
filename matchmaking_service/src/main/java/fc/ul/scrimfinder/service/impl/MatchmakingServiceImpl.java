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
import fc.ul.scrimfinder.mapper.LobbyMapper;
import fc.ul.scrimfinder.mapper.MatchMapper;
import fc.ul.scrimfinder.mapper.MatchTicketMapper;
import fc.ul.scrimfinder.repository.*;
import fc.ul.scrimfinder.service.MatchmakingService;
import fc.ul.scrimfinder.util.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
public class MatchmakingServiceImpl implements MatchmakingService {

    private static final int K_FACTOR = 40;
    @Inject
    PlayerRepository playerRepository;
    @Inject
    QueueRepository queueRepository;
    @Inject
    MatchTicketRepository ticketRepository;
    @Inject
    LobbyRepository lobbyRepository;
    @Inject
    MatchRepository matchRepository;
    @Inject
    RedisMatchmakingRepository redisRepository;
    @Inject
    DistributedLockService lockService;
    @Inject
    MatchTicketMapper ticketMapper;
    @Inject
    LobbyMapper lobbyMapper;
    @Inject
    MatchMapper matchMapper;
    @Inject
    @RestClient
    RankingServiceClient rankingServiceClient;

    @Override
    @Transactional
    public MatchTicketDTO joinQueue(JoinQueueRequest request) {
        Player player = playerRepository.findByIdOptional(request.getPlayerId())
                .orElseThrow(() -> new PlayerNotFoundException("Player not found: " + request.getPlayerId()));

        Queue queue = queueRepository.findByIdOptional(request.getQueueId())
                .orElseThrow(() -> new QueueNotFoundException("Queue not found: " + request.getQueueId()));

        PlayerRankingDTO ranking = rankingServiceClient.getPlayerRanking(player.getId(), queue.getId());

        if (ranking == null || ranking.lolAccountPPUID() == null) {
            throw new LeagueAccountNotLinkedException("A valid League Account must be linked before joining the queue.");
        }

        MatchTicket ticket = new MatchTicket();
        ticket.setPlayer(player);
        ticket.setQueue(queue);
        ticket.setRole(request.getRole() != null ? request.getRole() : Role.NONE);
        ticket.setStatus(TicketStatus.IN_QUEUE);
        ticket.setMmr(ranking.mmr());
        ticketRepository.persist(ticket);

        redisRepository.addTicket(queue.getId(), ticket.getRole(), ticket.getId(), ticket.getMmr());

        processQueue(queue);

        return ticketMapper.toDTO(ticket);
    }

    private void processQueue(Queue queue) {
        String lockKey = "lock:matchmaking:queue:" + queue.getId();
        if (!lockService.acquireLock(lockKey, Duration.ofSeconds(10))) {
            return;
        }

        try {
            if (queue.isRoleQueue()) {
                processRoleQueue(queue);
            } else {
                processStandardQueue(queue);
            }
        } finally {
            lockService.releaseLock(lockKey);
        }
    }

    private void processStandardQueue(Queue queue) {
        List<Long> ticketIds = redisRepository.getTickets(queue.getId(), Role.NONE);
        if (ticketIds.size() < queue.getRequiredPlayers()) return;

        List<MatchTicket> tickets = ticketIds.stream()
                .map(id -> ticketRepository.findById(id))
                .sorted(Comparator.comparingInt(MatchTicket::getMmr))
                .collect(Collectors.toList());

        if (queue.getMode() == MatchmakingMode.RANK_BASED) {
            for (int i = 0; i <= tickets.size() - queue.getRequiredPlayers(); i++) {
                MatchTicket min = tickets.get(i);
                MatchTicket max = tickets.get(i + queue.getRequiredPlayers() - 1);
                if (max.getMmr() - min.getMmr() <= queue.getMmrWindow()) {
                    List<MatchTicket> matched = new ArrayList<>(tickets.subList(i, i + queue.getRequiredPlayers()));
                    createMatchProposal(queue, matched);
                    return;
                }
            }
        } else {
            createMatchProposal(queue, tickets.subList(0, queue.getRequiredPlayers()));
        }
    }

    private void processRoleQueue(Queue queue) {
        Map<Role, List<Long>> ticketIdsByRole = new HashMap<>();
        for (Role role : Role.values()) {
            if (role == Role.NONE) continue;
            ticketIdsByRole.put(role, redisRepository.getTickets(queue.getId(), role));
        }

        if (hasEnoughForRoleQueueRedis(ticketIdsByRole)) {
            List<MatchTicket> matched = tryFindRoleMatch(queue, ticketIdsByRole);
            if (matched != null) {
                createMatchProposal(queue, matched);
            }
        }
    }

    private boolean hasEnoughForRoleQueueRedis(Map<Role, List<Long>> byRole) {
        return byRole.getOrDefault(Role.TOP, List.of()).size() >= 2 &&
                byRole.getOrDefault(Role.JUNGLE, List.of()).size() >= 2 &&
                byRole.getOrDefault(Role.MID, List.of()).size() >= 2 &&
                byRole.getOrDefault(Role.BOT, List.of()).size() >= 2 &&
                byRole.getOrDefault(Role.SUPP, List.of()).size() >= 2;
    }

    private List<MatchTicket> tryFindRoleMatch(Queue queue, Map<Role, List<Long>> ticketIdsByRole) {
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
    protected void createMatchProposal(Queue queue, List<MatchTicket> matchedTickets) {
        for (MatchTicket t : matchedTickets) {
            if (!redisRepository.removeTicket(queue.getId(), t.getRole(), t.getId())) {
                return;
            }
        }

        Lobby lobby = new Lobby();
        lobby.setQueue(queue);
        lobbyRepository.persist(lobby);
        if (queue.isRoleQueue()) {
            Map<Role, List<MatchTicket>> byRole = matchedTickets.stream()
                    .collect(Collectors.groupingBy(MatchTicket::getRole));
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
    }

    @Override
    @Transactional
    public MatchDTO acceptMatch(Long matchId, Long playerId) {
        Match match = matchRepository.findByIdOptional(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        if (match.getState() != MatchState.PENDING_ACCEPTANCE) {
            throw new RuntimeException("Match is not in acceptance phase");
        }

        match.getAcceptedPlayerIds().add(playerId);
        matchRepository.persist(match);

        if (match.getAcceptedPlayerIds().size() == match.getLobby().getQueue().getRequiredPlayers()) {
            match.setState(MatchState.IN_PROGRESS);
            match.setStartedAt(LocalDateTime.now());
            matchRepository.persist(match);
        }

        return matchMapper.toDTO(match);
    }

    @Override
    @Transactional
    public void declineMatch(Long matchId, Long playerId) {
        Match match = matchRepository.findByIdOptional(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        match.setState(MatchState.CANCELLED);
        match.setEndedAt(LocalDateTime.now());

        for (MatchTicket t : match.getLobby().getTickets()) {
            if (t.getPlayer().getId().equals(playerId)) {
                t.setStatus(TicketStatus.CANCELLED);
            } else {
                t.setStatus(TicketStatus.IN_QUEUE);
                t.setLobby(null);
                t.setTeam(null);
                redisRepository.addTicket(match.getLobby().getQueue().getId(), t.getRole(), t.getId(), t.getMmr());
            }
            ticketRepository.persist(t);
        }
        matchRepository.persist(match);
    }

    @Override
    @Transactional
    public void linkMatch(Long matchId, String externalGameId) {
        Match match = matchRepository.findByIdOptional(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));
        match.setExternalGameId(externalGameId);
        matchRepository.persist(match);
    }

    @Override
    @Transactional
    public void completeMatch(Long matchId) {
        Match match = matchRepository.findByIdOptional(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        if (match.getExternalGameId() == null) {
            throw new RuntimeException("Match must be linked with a valid League of Legends Game ID first.");
        }

        if (match.getState() == MatchState.COMPLETED) {
            return;
        }

        // 1. Calculate deltas
        List<MatchTicket> team1 = match.getLobby().getTickets().stream()
                .filter(t -> t.getTeam() != null && t.getTeam() == 1)
                .toList();
        List<MatchTicket> team2 = match.getLobby().getTickets().stream()
                .filter(t -> t.getTeam() != null && t.getTeam() == 2)
                .toList();

        double avgMMR1 = team1.stream().mapToInt(MatchTicket::getMmr).average().orElse(0);
        double avgMMR2 = team2.stream().mapToInt(MatchTicket::getMmr).average().orElse(0);

        Map<Long, MatchResultRequest.PlayerDelta> deltas = new HashMap<>();
        for (MatchTicket t : team1) {
            double expected = 1.0 / (1.0 + Math.pow(10.0, (avgMMR2 - t.getMmr()) / 400.0));
            int winDelta = (int) Math.round(K_FACTOR * (1.0 - expected));
            int lossDelta = (int) Math.round(K_FACTOR * expected);
            deltas.put(t.getPlayer().getId(), new MatchResultRequest.PlayerDelta(winDelta, lossDelta));
        }
        for (MatchTicket t : team2) {
            double expected = 1.0 / (1.0 + Math.pow(10.0, (avgMMR1 - t.getMmr()) / 400.0));
            int winDelta = (int) Math.round(K_FACTOR * (1.0 - expected));
            int lossDelta = (int) Math.round(K_FACTOR * expected);
            deltas.put(t.getPlayer().getId(), new MatchResultRequest.PlayerDelta(winDelta, lossDelta));
        }

        MatchResultRequest result = new MatchResultRequest(
                match.getExternalGameId(),
                match.getLobby().getQueue().getId(),
                deltas
        );

        match.setState(MatchState.COMPLETED);
        match.setEndedAt(LocalDateTime.now());
        matchRepository.persist(match);

        try {
            rankingServiceClient.reportMatchResults(result);
        } catch (Exception e) {
            log.error("Failed to report match results for match {}: {}", matchId, e.getMessage());
            match.setState(MatchState.RESULT_REPORTING_FAILED);
            matchRepository.persist(match);
            throw new RuntimeException("Match completed but results failed to sync. State: RESULT_REPORTING_FAILED", e);
        }
    }

    @Override
    @Transactional
    public void leaveQueue(Long ticketId) {
        MatchTicket ticket = ticketRepository.findByIdOptional(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Ticket not found: " + ticketId));

        if (ticket.getStatus() == TicketStatus.IN_QUEUE) {
            ticket.setStatus(TicketStatus.CANCELLED);
            ticketRepository.persist(ticket);
            redisRepository.removeTicket(ticket.getQueue().getId(), ticket.getRole(), ticket.getId());
        }
    }

    @Override
    public LobbyDTO getLobbyByTicket(Long ticketId) {
        MatchTicket ticket = ticketRepository.findByIdOptional(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("Ticket not found: " + ticketId));
        if (ticket.getLobby() == null) return null;
        return lobbyMapper.toDTO(ticket.getLobby());
    }
}
