package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.domain.QueueEntity;
import fc.ul.scrimfinder.dto.request.UpdateQueueRequest;
import fc.ul.scrimfinder.dto.response.QueueDTO;
import fc.ul.scrimfinder.exception.MMRAlreadyExistsException;
import fc.ul.scrimfinder.exception.QueueNotFoundException;
import fc.ul.scrimfinder.mapper.QueueMapper;
import fc.ul.scrimfinder.repository.QueueRepository;
import fc.ul.scrimfinder.service.QueueService;
import fc.ul.scrimfinder.util.MMRRuleType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

@ApplicationScoped
public class QueueServiceImpl implements QueueService {

    @Inject QueueRepository queueRepository;

    @Inject QueueMapper queueMapper;

    @Override
    @Transactional
    public QueueDTO createQueue(
            UUID queueId, String name, MMRRuleType mmrRuleType, @Positive int initialMMR) {
        if (queueRepository.findByIdOptional(queueId).isPresent()) {
            throw new MMRAlreadyExistsException("Queue with this ID already exists");
        }

        QueueEntity queue = new QueueEntity();
        queue.setId(queueId);
        queue.setName(name != null ? name : "Queue " + queueId);
        queue.setMmrRuleType(mmrRuleType);
        queue.setInitialMMR(initialMMR);
        queue.setActive(true);

        queueRepository.persist(queue);
        return queueMapper.toDTO(queue);
    }

    @Override
    @Transactional
    public QueueDTO updateQueue(UUID queueId, @NotNull @Valid UpdateQueueRequest updateDTO) {
        QueueEntity queue =
                queueRepository
                        .findByIdOptional(queueId)
                        .orElseThrow(() -> new QueueNotFoundException("Queue not found"));

        updateDTO.name().ifPresent(queue::setName);
        updateDTO.mmrRuleType().ifPresent(queue::setMmrRuleType);
        updateDTO.initialMMR().ifPresent(queue::setInitialMMR);
        updateDTO.active().ifPresent(queue::setActive);

        queueRepository.persist(queue);
        return queueMapper.toDTO(queue);
    }

    @Override
    @Transactional
    public QueueDTO deleteQueue(UUID queueId) throws QueueNotFoundException {
        QueueEntity queue =
                queueRepository
                        .findByIdOptional(queueId)
                        .orElseThrow(() -> new QueueNotFoundException("Queue not found"));

        QueueDTO dto = queueMapper.toDTO(queue);
        queueRepository.delete(queue);
        return dto;
    }
}
