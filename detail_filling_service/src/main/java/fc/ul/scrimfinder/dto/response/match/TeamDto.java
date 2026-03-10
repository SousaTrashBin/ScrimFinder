package fc.ul.scrimfinder.dto.response.match;

import java.util.List;

public record TeamDto(
        List<BanDto> bans,
        ObjectivesDto objectives,
        Integer teamId,
        Boolean win
) {
}
