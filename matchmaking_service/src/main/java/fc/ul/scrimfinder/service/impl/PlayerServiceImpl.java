package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.client.RankingServiceClient;
import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.dto.response.PlayerDTO;
import fc.ul.scrimfinder.exception.PlayerNotFoundException;
import fc.ul.scrimfinder.grpc.ExternalPlayerFillingService;
import fc.ul.scrimfinder.mapper.PlayerMapper;
import fc.ul.scrimfinder.repository.PlayerRepository;
import fc.ul.scrimfinder.service.PlayerService;
import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.inject.RestClient;

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
    public PlayerDTO createPlayer(UUID id, String username) {
        if (playerRepository.findByIdOptional(id).isPresent()) {
            throw new RuntimeException("Player with this ID already exists locally");
        }

        Player player = new Player();
        player.setId(id);
        player.setUsername(username);
        playerRepository.persist(player);

        rankingServiceClient.registerPlayer(id, username);
        return playerMapper.toDTO(player);
    }

    @Override
    public PlayerDTO getPlayer(UUID id) {
        Player player =
                playerRepository
                        .findByIdOptional(id)
                        .orElseThrow(() -> new PlayerNotFoundException("Player not found: " + id));
        return playerMapper.toDTO(player);
    }
}
