package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.client.RankingServiceClient;
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
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Slf4j
@Blocking
@ApplicationScoped
public class PlayerServiceImpl implements PlayerService {

    @Inject PlayerRepository playerRepository;

    @Inject PlayerMapper playerMapper;

    @Inject @RestClient RankingServiceClient rankingServiceClient;

    @Inject
    @GrpcClient("player-service")
    ExternalPlayerFillingService playerFillingClient;

    @Override
    @Transactional
    public PlayerDTO createPlayer(String username) {
        log.info("Attempting to create player with username: {}", username);
        if (username == null || username.isBlank()) {
            log.error("Player creation failed: Username is null or blank");
            throw new IllegalArgumentException("Username is required");
        }
        if (playerRepository.find("username", username).firstResultOptional().isPresent()) {
            log.warn("Player creation failed: Username {} already exists", username);
            throw new PlayerAlreadyExistsException(
                    "Player with username " + username + " already exists");
        }

        Player player = new Player();
        player.setUsername(username);
        playerRepository.persist(player);
        log.info("Player {} persisted locally with ID: {}", username, player.getId());

        try {
            rankingServiceClient.registerPlayer(player.getId(), username);
            log.info("Player {} successfully registered in Ranking Service", username);
        } catch (Exception e) {
            log.error("Failed to register player {} in Ranking Service: {}", username, e.getMessage());
            throw e;
        }

        return playerMapper.toDTO(player);
    }

    @Override
    public PlayerDTO getPlayer(UUID id) {
        log.debug("Fetching player with ID: {}", id);
        Player player =
                playerRepository
                        .findByIdOptional(id)
                        .orElseThrow(
                                () -> {
                                    log.warn("Player not found with ID: {}", id);
                                    return new PlayerNotFoundException("Player not found: " + id);
                                });
        return playerMapper.toDTO(player);
    }
}
