package fc.ul.scrimfinder.dto.response;

import fc.ul.scrimfinder.util.MMRRuleType;

public record QueueDTO(
        Long id,
        String name,
        MMRRuleType mmrRuleType,
        int initialMMR,
        boolean active
) {
}
