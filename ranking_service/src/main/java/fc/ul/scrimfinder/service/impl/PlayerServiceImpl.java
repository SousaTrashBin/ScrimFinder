package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.domain.RiotAccount;
import fc.ul.scrimfinder.dto.external.ExternalPlayerResponse;
import fc.ul.scrimfinder.dto.response.PlayerDTO;
import fc.ul.scrimfinder.dto.response.RankDTO;
import fc.ul.scrimfinder.dto.response.RiotAccountDTO;
import fc.ul.scrimfinder.exception.*;
import fc.ul.scrimfinder.mapper.PlayerMapper;
import fc.ul.scrimfinder.repository.PlayerRepository;
import fc.ul.scrimfinder.repository.ReadOnlyPlayerRepository;
import fc.ul.scrimfinder.repository.RiotAccountRepository;
import fc.ul.scrimfinder.rest.client.ExternalPlayerClient;
import fc.ul.scrimfinder.service.PlayerService;
import fc.ul.scrimfinder.util.MMRConverter;
import fc.ul.scrimfinder.util.Region;
import fc.ul.scrimfinder.util.Tier;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Slf4j
@Blocking
@ApplicationScoped
public class PlayerServiceImpl implements PlayerService {

    @Inject PlayerRepository playerRepository;

    @Inject ReadOnlyPlayerRepository readOnlyPlayerRepository;

    @Inject RiotAccountRepository riotAccountRepository;

    @Inject PlayerMapper playerMapper;

    @Inject @RestClient ExternalPlayerClient externalPlayerClient;

    @Inject MMRConverter mmrConverter;

    @Override
    @Transactional
    public PlayerDTO createPlayer(UUID id, String discordUsername) {
        log.info(
                "\u001B[33m[PENDING]\u001B[0m Creating player profile in Ranking Service. ID: {}, Username: {}",
                id,
                discordUsername);
        if (playerRepository.findByIdOptional(id).isPresent()) {
            log.warn(
                    "\u001B[31m[ERROR]\u001B[0m Player profile creation failed: ID {} already exists", id);
            throw new PlayerAlreadyCreatedException("There's already a player created with that ID");
        }
        if (playerRepository.find("discordUsername", discordUsername).count() > 0) {
            log.warn(
                    "\u001B[31m[ERROR]\u001B[0m Player profile creation failed: Discord username {} already exists",
                    discordUsername);
            throw new PlayerAlreadyCreatedException(
                    "There's already a player created with that discord username");
        }
        Player player = new Player();
        player.setId(id);
        player.setDiscordUsername(discordUsername);
        playerRepository.persist(player);
        log.info("\u001B[32m[SUCCESS]\u001B[0m Player profile created successfully for ID: {}", id);
        return playerMapper.toDTO(player);
    }

    @Override
    public PlayerDTO getPlayer(UUID id) {
        log.debug("\u001B[34m[INFO]\u001B[0m Fetching player profile with ID: {}", id);
        Player player =
                readOnlyPlayerRepository
                        .findByIdOptional(id)
                        .orElseThrow(
                                () -> {
                                    log.warn(
                                            "\u001B[31m[ERROR]\u001B[0m Player profile fetch failed: ID {} not found",
                                            id);
                                    return new PlayerNotFoundException("Player not found: " + id);
                                });
        return playerMapper.toDTO(player);
    }

    @Override
    public RiotAccountDTO getPrimaryAccount(UUID playerId) {
        Player player =
                readOnlyPlayerRepository
                        .findByIdOptional(playerId)
                        .orElseThrow(() -> new PlayerNotFoundException("Player not found: " + playerId));
        RiotAccount primary = player.getPrimaryAccount();
        if (primary == null) {
            throw new LeagueAccountNotLinkedException(
                    "Player must link a League of Legends account first");
        }

        return new RiotAccountDTO(
                primary.getId(),
                primary.getPuuid(),
                primary.getGameName(),
                primary.getTagLine(),
                primary.getRegion(),
                primary.isPrimary());
    }

    @Override
    @Transactional
    @Retry(maxRetries = 3, delay = 500)
    @Timeout(10000)
    public PlayerDTO linkLolAccount(
            UUID playerId, String puuid, String gameName, String tagLine, Region region) {
        log.info(
                "\u001B[33m[PENDING]\u001B[0m Linking Riot account to player {}. Account: {}#{}",
                playerId,
                gameName,
                tagLine);
        Player player =
                playerRepository
                        .findByIdOptional(playerId)
                        .orElseThrow(
                                () -> {
                                    log.warn(
                                            "\u001B[31m[ERROR]\u001B[0m Link account failed: Player {} not found",
                                            playerId);
                                    return new PlayerNotFoundException("Internal player not found");
                                });

        ExternalPlayerResponse externalPlayer;
        try {
            externalPlayer =
                    externalPlayerClient.fetchPlayerRank(region.getDisplayName(), gameName, tagLine);
            log.info(
                    "\u001B[32m[SUCCESS]\u001B[0m Verified Riot account {}#{} via External Service",
                    gameName,
                    tagLine);
        } catch (Exception e) {
            log.error(
                    "\u001B[31m[ERROR]\u001B[0m External service verification failed for {}#{}: {}",
                    gameName,
                    tagLine,
                    e.getMessage());
            throw new ExternalServiceUnavailableException(
                    "External service is currently unavailable or player not found. Please try again later.");
        }

        String effectivePuuid =
                (puuid != null && !puuid.isBlank()) ? puuid : externalPlayer.account().puuid();

        if (player.getRiotAccounts().stream().anyMatch(acc -> acc.getPuuid().equals(effectivePuuid))) {
            log.warn(
                    "\u001B[33m[WARN]\u001B[0m Link account failed: Riot account {} already linked to player {}",
                    effectivePuuid,
                    playerId);
            throw new PlayerAlreadyCreatedException(
                    "This Riot account is already linked to this player.");
        }

        RiotAccount riotAccount = new RiotAccount();
        riotAccount.setPuuid(effectivePuuid);
        riotAccount.setGameName(gameName);
        riotAccount.setTagLine(tagLine);
        riotAccount.setRegion(region);
        riotAccount.setPlayer(player);

        if (player.getRiotAccounts().isEmpty()) {
            riotAccount.setPrimary(true);
        }

        player.getRiotAccounts().add(riotAccount);
        playerRepository.persistAndFlush(player);
        log.info(
                "\u001B[32m[SUCCESS]\u001B[0m Riot account linked successfully to player {}", playerId);

        return syncPlayerMMR(playerId);
    }

    @Override
    @Transactional
    public PlayerDTO setPrimaryAccount(UUID playerId, String puuid) {
        log.info(
                "\u001B[33m[PENDING]\u001B[0m Setting primary account for player {}. New primary PUUID: {}",
                playerId,
                puuid);
        Player player =
                playerRepository
                        .findByIdOptional(playerId)
                        .orElseThrow(
                                () -> {
                                    log.warn(
                                            "\u001B[31m[ERROR]\u001B[0m Set primary account failed: Player {} not found",
                                            playerId);
                                    return new PlayerNotFoundException("Player not found");
                                });

        RiotAccount newPrimary =
                player.getRiotAccounts().stream()
                        .filter(acc -> acc.getPuuid().equals(puuid))
                        .findFirst()
                        .orElseThrow(
                                () -> {
                                    log.warn(
                                            "\u001B[31m[ERROR]\u001B[0m Set primary account failed: Account {} not linked to player {}",
                                            puuid,
                                            playerId);
                                    return new ExternalAccountNotFoundException(
                                            "Riot account not found for this player");
                                });

        player.getRiotAccounts().forEach(acc -> acc.setPrimary(false));
        newPrimary.setPrimary(true);

        playerRepository.persist(player);
        log.info("\u001B[32m[SUCCESS]\u001B[0m Primary account updated for player {}", playerId);
        return syncPlayerMMR(playerId);
    }

    @Override
    @Transactional
    @Retry(maxRetries = 3, delay = 500)
    @Timeout(10000)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 5000)
    public PlayerDTO syncPlayerMMR(UUID playerId)
            throws PlayerNotFoundException, LeagueAccountNotLinkedException {
        log.info("\u001B[33m[PENDING]\u001B[0m Syncing MMR for player {}", playerId);
        Player player =
                playerRepository
                        .findByIdOptional(playerId)
                        .orElseThrow(
                                () -> {
                                    log.warn(
                                            "\u001B[31m[ERROR]\u001B[0m Sync MMR failed: Player {} not found", playerId);
                                    return new PlayerNotFoundException("Player not found");
                                });

        RiotAccount primaryAccount = player.getPrimaryAccount();
        if (primaryAccount == null) {
            log.warn(
                    "\u001B[31m[ERROR]\u001B[0m Sync MMR failed: No primary account linked for player {}",
                    playerId);
            throw new LeagueAccountNotLinkedException(
                    "Player must link a League of Legends account first");
        }

        try {
            ExternalPlayerResponse externalPlayer =
                    externalPlayerClient.fetchPlayerRank(
                            primaryAccount.getRegion().getDisplayName(),
                            primaryAccount.getGameName(),
                            primaryAccount.getTagLine());

            if (externalPlayer.queues() != null) {
                externalPlayer
                        .queues()
                        .forEach(
                                entry -> {
                                    if (entry.rank() == null || entry.rank().tier() == null) return;
                                    RankDTO rankDTO =
                                            new RankDTO(
                                                    Tier.valueOf(entry.rank().tier().toUpperCase()),
                                                    entry.rank().division(),
                                                    entry.rank().lps());
                                    if ("RANKED_SOLO_5x5".equals(entry.queueType())) {
                                        int oldMMR = player.getSoloqMMR();
                                        player.setSoloqMMR(mmrConverter.convertRankToMMR(rankDTO));
                                        log.info(
                                                "\u001B[34m[INFO]\u001B[0m Updated SOLOQ MMR for {}: {} -> {}",
                                                player.getDiscordUsername(),
                                                oldMMR,
                                                player.getSoloqMMR());
                                    } else if ("RANKED_FLEX_SR".equals(entry.queueType())) {
                                        int oldMMR = player.getFlexMMR();
                                        player.setFlexMMR(mmrConverter.convertRankToMMR(rankDTO));
                                        log.info(
                                                "\u001B[34m[INFO]\u001B[0m Updated FLEX MMR for {}: {} -> {}",
                                                player.getDiscordUsername(),
                                                oldMMR,
                                                player.getFlexMMR());
                                    }
                                });
            }

            playerRepository.persist(player);
            log.info("\u001B[32m[SUCCESS]\u001B[0m MMR sync complete for player {}", playerId);
        } catch (Exception e) {
            log.error(
                    "\u001B[31m[ERROR]\u001B[0m Failed to sync MMR for player {}: {}",
                    playerId,
                    e.getMessage());
            throw e;
        }
        return playerMapper.toDTO(player);
    }

    @Override
    @Transactional
    public void unlinkLolAccount(String gameName, String tagLine) {
        log.info("\u001B[33m[PENDING]\u001B[0m Unlinking Riot account {}#{}", gameName, tagLine);
        Optional<RiotAccount> account =
                riotAccountRepository
                        .find("gameName = ?1 and tagLine = ?2", gameName, tagLine)
                        .firstResultOptional();

        if (account.isPresent()) {
            RiotAccount riotAccount = account.get();
            Player player = riotAccount.getPlayer();
            player.getRiotAccounts().remove(riotAccount);
            riotAccountRepository.delete(riotAccount);
            playerRepository.persistAndFlush(player);
            log.info(
                    "\u001B[32m[SUCCESS]\u001B[0m Riot account {}#{} unlinked successfully",
                    gameName,
                    tagLine);
        } else {
            log.info(
                    "\u001B[34m[INFO]\u001B[0m Riot account {}#{} not found. Nothing to unlink.",
                    gameName,
                    tagLine);
        }
    }
}
