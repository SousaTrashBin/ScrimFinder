package fc.ul.scrimfinder.dto.request;

public record MatchAddDto(
        Long riotMatchId,
        MatchStats matchStats
) {
}
