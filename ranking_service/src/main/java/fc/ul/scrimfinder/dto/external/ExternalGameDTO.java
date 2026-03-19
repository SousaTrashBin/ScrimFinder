package fc.ul.scrimfinder.dto.external;

import java.util.List;

public record ExternalGameDTO(String gameId, List<Long> winners, List<Long> losers) {}
