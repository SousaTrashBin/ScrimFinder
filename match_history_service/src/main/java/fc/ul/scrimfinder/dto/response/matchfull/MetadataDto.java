package fc.ul.scrimfinder.dto.response.matchfull;

import java.util.List;

public record MetadataDto(
        String dataVersion,
        String matchId,
        List<String> participants
) {
}
