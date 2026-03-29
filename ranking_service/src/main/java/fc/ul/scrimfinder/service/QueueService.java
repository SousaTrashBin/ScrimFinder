package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.request.UpdateQueueRequest;
import fc.ul.scrimfinder.dto.response.QueueDTO;
import fc.ul.scrimfinder.exception.QueueNotFoundException;
import fc.ul.scrimfinder.util.MMRRuleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public interface QueueService {
    QueueDTO createQueue(UUID queueId, String name, MMRRuleType MMRRuleType, @Positive int initialMMR)
            throws QueueNotFoundException;

    QueueDTO updateQueue(UUID queueId, @NotNull @Valid UpdateQueueRequest updateDTO)
            throws QueueNotFoundException;

    QueueDTO deleteQueue(UUID queueId) throws QueueNotFoundException;
}
