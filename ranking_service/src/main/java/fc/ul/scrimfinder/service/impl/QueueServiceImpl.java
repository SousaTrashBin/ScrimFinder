package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.dto.request.UpdateQueueRequest;
import fc.ul.scrimfinder.dto.response.QueueDTO;
import fc.ul.scrimfinder.exception.QueueNotFoundException;
import fc.ul.scrimfinder.mapper.QueueMapper;
import fc.ul.scrimfinder.repository.QueueRepository;
import fc.ul.scrimfinder.service.QueueService;
import fc.ul.scrimfinder.util.MMRRuleType;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class QueueServiceImpl implements QueueService {
    QueueRepository queueRepository;
    QueueMapper queueMapper;

    @Override
    public QueueDTO createQueue(Long queueId, MMRRuleType MMRRuleType, int initialMMR) {
        return null;
    }

    @Override
    public QueueDTO updateQueue(Long queueId, UpdateQueueRequest updateDTO) {
        return null;
    }

    @Override
    public QueueDTO deleteQueue(Long queueId) throws QueueNotFoundException {
        return null;
    }
}
