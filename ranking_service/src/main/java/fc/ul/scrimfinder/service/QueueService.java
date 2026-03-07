package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.request.UpdateQueueRequest;
import fc.ul.scrimfinder.dto.response.QueueDTO;
import fc.ul.scrimfinder.exception.QueueNotFoundException;
import fc.ul.scrimfinder.util.MMRRuleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public interface QueueService {
    QueueDTO createQueue(Long queueId, MMRRuleType MMRRuleType, @Positive int initialMMR) throws QueueNotFoundException;

    QueueDTO updateQueue(Long queueId, @NotNull @Valid UpdateQueueRequest updateDTO) throws QueueNotFoundException;

    QueueDTO deleteQueue(Long queueId) throws QueueNotFoundException;
}
