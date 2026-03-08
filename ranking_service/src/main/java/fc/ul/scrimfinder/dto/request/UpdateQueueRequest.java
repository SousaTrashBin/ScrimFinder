package fc.ul.scrimfinder.dto.request;

import fc.ul.scrimfinder.util.MMRRuleType;

import java.util.Optional;

public record UpdateQueueRequest(
        Optional<String> name,
        Optional<MMRRuleType> mmrRuleType,
        Optional<Integer> initialMMR,
        Optional<Boolean> active
) {
}
