package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.dto.response.PlayerDTO;
import fc.ul.scrimfinder.exception.PlayerAlreadyExistsException;
import fc.ul.scrimfinder.exception.PlayerNotFoundException;
import fc.ul.scrimfinder.grpc.ExternalPlayerFillingService;
import fc.ul.scrimfinder.mapper.PlayerMapper;
import fc.ul.scrimfinder.repository.PlayerRepository;
import fc.ul.scrimfinder.service.PlayerService;
import io.quarkus.grpc.GrpcClient;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Blocking
@ApplicationScoped
public class PlayerServiceImpl implements PlayerService {

    @Inject PlayerRepository playerRepository;

    @Inject PlayerMapper playerMapper;

    @Inject
    @GrpcClient("ranking-service")
    fc.ul.scrimfinder.grpc.RankingService rankingGrpcClient;

    @Inject
    @GrpcClient("player-service")
    ExternalPlayerFillingService playerFillingClient;

    @Override
    @Transactional
    public PlayerDTO createPlayer(UUID id, String discordUsername) {
        log.info(
                "\u001B[33m[PENDING]\u001B[0m Attempting to create player with discordUsername: {} and id: {}",
                discordUsername,
                id);
        if (discordUsername == null || discordUsername.isBlank()) {
            log.error(
                    "\u001B[31m[ERROR]\u001B[0m Player creation failed: Discord Username is null or blank");
            throw new IllegalArgumentException("Discord Username is required");
        }
        if (playerRepository
                .find("discordUsername", discordUsername)
                .firstResultOptional()
                .isPresent()) {
            log.warn(
                    "\u001B[33m[WARN]\u001B[0m Player creation failed: Discord Username {} already exists",
                    discordUsername);
            throw new PlayerAlreadyExistsException(
                    "Player with discord username " + discordUsername + " already exists");
        }

        Player player = new Player();
        if (id != null) {
            player.setId(id);
        }
        player.setDiscordUsername(discordUsername);
        playerRepository.persist(player);
        log.info(
                "\u001B[32m[SUCCESS]\u001B[0m Player {} persisted locally with ID: {}",
                discordUsername,
                player.getId());

        try {
            fc.ul.scrimfinder.grpc.RegisterPlayerRequest gRpcRequest =
                    fc.ul.scrimfinder.grpc.RegisterPlayerRequest.newBuilder()
                            .setPlayerId(player.getId().toString())
                            .setUsername(discordUsername)
                            .build();
            var response =
                    rankingGrpcClient
                            .registerPlayer(gRpcRequest)
                            .await()
                            .atMost(java.time.Duration.ofSeconds(5));
            if (response.getSuccess()) {
                log.info(
                        "\u001B[32m[SUCCESS]\u001B[0m Player {} successfully registered in Ranking Service via gRPC",
                        discordUsername);
            } else {
                log.warn(
                        "\u001B[33m[WARN]\u001B[0m Ranking service returned failure for {}: {}",
                        discordUsername,
                        response.getMessage());
            }
        } catch (Exception e) {
            log.error(
                    "\u001B[31m[ERROR]\u001B[0m Failed to register player {} in Ranking Service via gRPC: {}",
                    discordUsername,
                    e.getMessage());
            throw e;
        }

        return playerMapper.toDTO(player);
    }

    @Override
    public PlayerDTO getPlayer(UUID id) {
        log.debug("\u001B[34m[INFO]\u001B[0m Fetching player with ID: {}", id);
        Player player =
                playerRepository
                        .findByIdOptional(id)
                        .orElseThrow(
                                () -> {
                                    log.warn("\u001B[33m[WARN]\u001B[0m Player not found with ID: {}", id);
                                    return new PlayerNotFoundException("Player not found: " + id);
                                });
        return playerMapper.toDTO(player);
    }
}
