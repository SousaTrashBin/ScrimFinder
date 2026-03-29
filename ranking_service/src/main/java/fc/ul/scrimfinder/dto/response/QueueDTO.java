package fc.ul.scrimfinder.dto.response;

import fc.ul.scrimfinder.util.MMRRuleType;
import java.util.UUID;

public record QueueDTO(
        UUID id, String name, MMRRuleType mmrRuleType, int initialMMR, boolean active) {}
