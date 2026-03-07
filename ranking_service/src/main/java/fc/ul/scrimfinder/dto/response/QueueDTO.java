package fc.ul.scrimfinder.dto.response;

import fc.ul.scrimfinder.util.MMRRuleType;

public record QueueDTO(Long queueId, Integer initialMMR, MMRRuleType mmrRuleType) {

}
