package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.domain.RiotAccount;
import fc.ul.scrimfinder.dto.response.PlayerDTO;
import fc.ul.scrimfinder.dto.response.RankDTO;
import fc.ul.scrimfinder.exception.*;
import fc.ul.scrimfinder.grpc.ExternalPlayerFillingService;
import fc.ul.scrimfinder.grpc.PlayerRequest;
import fc.ul.scrimfinder.grpc.PlayerResponse;
import fc.ul.scrimfinder.mapper.PlayerMapper;
import fc.ul.scrimfinder.repository.PlayerRepository;
import fc.ul.scrimfinder.service.PlayerService;
import fc.ul.scrimfinder.util.MMRConverter;
import fc.ul.scrimfinder.util.Region;
import fc.ul.scrimfinder.util.Tier;
import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

@ApplicationScoped
public class PlayerServiceImpl implements PlayerService {

    @Inject PlayerRepository playerRepository;

    @Inject PlayerMapper playerMapper;

    @Inject
    @GrpcClient("player-service")
    ExternalPlayerFillingService playerFillingClient;

    @Inject MMRConverter mmrConverter;

    @Override
    @Transactional
    public PlayerDTO createPlayer(UUID id, String username) {
        if (playerRepository.find("discordUsername", username).count() > 0) {
            throw new PlayerAlreadyCreatedException(
                    "There's already a player created with that discord username");
        }
        Player player = new Player();
        player.setId(id);
        player.setDiscordUsername(username);
        playerRepository.persist(player);
        return playerMapper.toDTO(player);
    }

    @Override
    @Transactional
    @Retry(maxRetries = 3, delay = 500)
    @Timeout(3000)
    public PlayerDTO linkLolAccount(
            UUID playerId, String puuid, String gameName, String tagLine, Region region) {
        Player player =
                playerRepository
                        .findByIdOptional(playerId)
                        .orElseThrow(() -> new PlayerNotFoundException("Internal player not found"));

        if (player.getRiotAccounts().stream().anyMatch(acc -> acc.getPuuid().equals(puuid))) {
            throw new PlayerAlreadyCreatedException(
                    "This Riot account is already linked to this player.");
        }

        try {
            playerFillingClient
                    .getPlayer(PlayerRequest.newBuilder().setGameName(gameName).setTagLine(tagLine).build())
                    .await()
                    .indefinitely();
        } catch (Exception e) {
            throw new ExternalServiceUnavailableException(
                    "External service is currently unavailable or player not found. Please try again later.");
        }

        RiotAccount riotAccount = new RiotAccount();
        riotAccount.setPuuid(puuid);
        riotAccount.setGameName(gameName);
        riotAccount.setTagLine(tagLine);
        riotAccount.setRegion(region);
        riotAccount.setPlayer(player);

        if (player.getRiotAccounts().isEmpty()) {
            riotAccount.setPrimary(true);
        }

        player.getRiotAccounts().add(riotAccount);
        playerRepository.persist(player);

        return syncPlayerMMR(playerId);
    }

    @Override
    @Transactional
    public PlayerDTO setPrimaryAccount(UUID playerId, String puuid) {
        Player player =
                playerRepository
                        .findByIdOptional(playerId)
                        .orElseThrow(() -> new PlayerNotFoundException("Player not found"));

        RiotAccount newPrimary =
                player.getRiotAccounts().stream()
                        .filter(acc -> acc.getPuuid().equals(puuid))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new ExternalAccountNotFoundException("Riot account not found for this player"));

        player.getRiotAccounts().forEach(acc -> acc.setPrimary(false));
        newPrimary.setPrimary(true);

        playerRepository.persist(player);
        return syncPlayerMMR(playerId);
    }

    @Override
    @Transactional
    @Retry(maxRetries = 2, delay = 500)
    @Timeout(4000)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 5000)
    public PlayerDTO syncPlayerMMR(UUID playerId)
            throws PlayerNotFoundException, LeagueAccountNotLinkedException {
        Player player =
                playerRepository
                        .findByIdOptional(playerId)
                        .orElseThrow(() -> new PlayerNotFoundException("Player not found"));

        RiotAccount primaryAccount = player.getPrimaryAccount();
        if (primaryAccount == null) {
            throw new LeagueAccountNotLinkedException(
                    "Player must link a League of Legends account first");
        }

        PlayerResponse externalPlayer =
                playerFillingClient
                        .getPlayer(
                                PlayerRequest.newBuilder()
                                        .setGameName(primaryAccount.getGameName())
                                        .setTagLine(primaryAccount.getTagLine())
                                        .build())
                        .await()
                        .indefinitely();

        externalPlayer
                .getEntriesList()
                .forEach(
                        entry -> {
                            RankDTO rankDTO =
                                    new RankDTO(
                                            Tier.valueOf(entry.getTier()),
                                            parseDivision(entry.getRank()),
                                            entry.getLeaguePoints());
                            if ("RANKED_SOLO_5x5".equals(entry.getQueueType())) {
                                player.setSoloqMMR(mmrConverter.convertRankToMMR(rankDTO));
                            } else if ("RANKED_FLEX_SR".equals(entry.getQueueType())) {
                                player.setFlexMMR(mmrConverter.convertRankToMMR(rankDTO));
                            }
                        });

        playerRepository.persist(player);
        return playerMapper.toDTO(player);
    }

    private int parseDivision(String rank) {
        try {
            return Integer.parseInt(rank);
        } catch (NumberFormatException e) {
            return switch (rank) {
                case "I" -> 1;
                case "II" -> 2;
                case "III" -> 3;
                case "IV" -> 4;
                default -> 1;
            };
        }
    }
}
