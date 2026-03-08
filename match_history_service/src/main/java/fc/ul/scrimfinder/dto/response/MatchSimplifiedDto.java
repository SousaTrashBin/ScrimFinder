package fc.ul.scrimfinder.dto.response;

import fc.ul.scrimfinder.dto.request.MatchAddDto;

public record MatchSimplifiedDto(
        Long matchId,
        MatchAddDto match
) {
}
