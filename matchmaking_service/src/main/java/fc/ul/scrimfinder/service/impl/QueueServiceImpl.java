package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.domain.Queue;
import fc.ul.scrimfinder.dto.response.QueueDTO;
import fc.ul.scrimfinder.exception.QueueNotFoundException;
import fc.ul.scrimfinder.mapper.QueueMapper;
import fc.ul.scrimfinder.repository.QueueRepository;
import fc.ul.scrimfinder.service.QueueService;
import fc.ul.scrimfinder.util.MatchmakingMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class QueueServiceImpl implements QueueService {

    @Inject
    QueueRepository queueRepository;

    @Inject
    QueueMapper queueMapper;

    @Override
    @Transactional
    public QueueDTO createQueue(Long id, String name, String namespace, int requiredPlayers, boolean isRoleQueue, MatchmakingMode mode, int mmrWindow) {
        Queue queue = new Queue();
        queue.setId(id);
        queue.setName(name);
        queue.setNamespace(namespace);
        queue.setRequiredPlayers(requiredPlayers);
        queue.setRoleQueue(isRoleQueue);
        queue.setMode(mode != null ? mode : MatchmakingMode.NORMAL);
        queue.setMmrWindow(mmrWindow);
        queueRepository.persist(queue);
        return queueMapper.toDTO(queue);
    }

    @Override
    public QueueDTO getQueue(Long id) {
        Queue queue = queueRepository.findByIdOptional(id)
                .orElseThrow(() -> new QueueNotFoundException("Queue not found: " + id));
        return queueMapper.toDTO(queue);
    }
}
