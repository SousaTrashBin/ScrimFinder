package fc.ul.scrimfinder.dto.response.player;

public record MiniSeriesDto(
        Integer losses,
        String progress,
        Integer target,
        Integer wins
) {
}
