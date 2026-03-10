package fc.ul.scrimfinder.dto.response.matchfull;

import java.util.List;

public record TeamDto(
        List<BanDto> bans,
        ObjectivesDto objectives,
        Integer teamId,
        Boolean win
) {
}
