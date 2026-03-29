package fc.ul.scrimfinder.dto.external;

import java.util.List;

public record ExternalGameDTO(String riotMatchId, List<ExternalPlayerStatsDTO> players) {
}
