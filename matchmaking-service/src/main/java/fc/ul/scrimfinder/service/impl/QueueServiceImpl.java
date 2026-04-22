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

    @Inject QueueMapper queueMapper;

    @Inject @org.eclipse.microprofile.rest.client.inject.RestClient
    fc.ul.scrimfinder.client.RankingServiceClient rankingServiceClient;

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
            rankingServiceClient.createQueue(id, name, 1000);
            log.info("\u001B[32m[SUCCESS]\u001B[0m Queue {} registered in Ranking Service", id);
        } catch (Exception e) {
            log.warn(
                    "\u001B[33m[WARN]\u001B[0m Could not register queue {} in Ranking Service: {}",
                    id,
                    e.getMessage());
        }

        return queueMapper.toDTO(queue);
    }

    @Override
    public QueueDTO getQueue(UUID id) {
        log.debug("\u001B[34m[INFO]\u001B[0m Fetching queue: {}", id);
        Queue queue =
                queueRepository
                        .findByIdOptional(id)
                        .orElseThrow(() -> new QueueNotFoundException("Queue not found: " + id));
        return queueMapper.toDTO(queue);
    }
}
