package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.domain.Queue;
import fc.ul.scrimfinder.dto.response.QueueDTO;
import fc.ul.scrimfinder.exception.QueueNotFoundException;
import fc.ul.scrimfinder.mapper.QueueMapper;
import fc.ul.scrimfinder.repository.QueueRepository;
import fc.ul.scrimfinder.service.QueueService;
import fc.ul.scrimfinder.util.MatchmakingMode;
import fc.ul.scrimfinder.util.Region;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class QueueServiceImpl implements QueueService {

    @Inject QueueRepository queueRepository;

    @Inject fc.ul.scrimfinder.repository.ReadOnlyQueueRepository readOnlyQueueRepository;

    @Inject QueueMapper queueMapper;

    @Inject
    @io.quarkus.grpc.GrpcClient("ranking-service")
    fc.ul.scrimfinder.grpc.RankingService rankingGrpcClient;

    @Override
    @Transactional
    public QueueDTO createQueue(
            UUID id,
            String name,
            String namespace,
            int requiredPlayers,
            boolean isRoleQueue,
            MatchmakingMode mode,
            int mmrWindow,
            Region region) {
        log.info("\u001B[33m[PENDING]\u001B[0m Creating queue: {} (ID: {})", name, id);
        Queue queue = new Queue();
        queue.setId(id);
        queue.setName(name);
        queue.setNamespace(namespace);
        queue.setRequiredPlayers(requiredPlayers);
        queue.setRoleQueue(isRoleQueue);
        queue.setMode(mode != null ? mode : MatchmakingMode.NORMAL);
        queue.setMmrWindow(mmrWindow);
        queue.setRegion(region);
        queueRepository.persist(queue);

        try {
            fc.ul.scrimfinder.grpc.CreateQueueRequest gRpcRequest =
                    fc.ul.scrimfinder.grpc.CreateQueueRequest.newBuilder()
                            .setQueueId(id.toString())
                            .setName(name)
                            .setInitialMMR(1000)
                            .build();
            var response =
                    rankingGrpcClient
                            .createQueue(gRpcRequest)
                            .await()
                            .atMost(java.time.Duration.ofSeconds(5));
            if (response.getSuccess()) {
                log.info(
                        "\u001B[32m[SUCCESS]\u001B[0m Queue {} registered in Ranking Service via gRPC", id);
            } else {
                log.error(
                        "\u001B[31m[ERROR]\u001B[0m Ranking service returned failure for {}: {}",
                        id,
                        response.getMessage());
                throw new RuntimeException(
                        "Failed to register queue in Ranking Service: " + response.getMessage());
            }
        } catch (Exception e) {
            log.error(
                    "\u001B[31m[ERROR]\u001B[0m Could not register queue {} in Ranking Service via gRPC: {}",
                    id,
                    e.getMessage());
            throw new RuntimeException("Queue creation failed due to Ranking Service error", e);
        }

        return queueMapper.toDTO(queue);
    }

    @Override
    public QueueDTO getQueue(UUID id) {
        log.info("\u001B[34m[INFO]\u001B[0m Fetching queue (from Read Replica): {}", id);
        Queue queue =
                readOnlyQueueRepository
                        .findByIdOptional(id)
                        .orElseThrow(() -> new QueueNotFoundException("Queue not found: " + id));
        return queueMapper.toDTO(queue);
    }
}
