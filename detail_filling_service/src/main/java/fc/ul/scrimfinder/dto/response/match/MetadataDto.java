package fc.ul.scrimfinder.dto.response.match;

import java.util.List;

public record MetadataDto(
        String dataVersion,
        String matchId,
        List<String> participants
) {
}
