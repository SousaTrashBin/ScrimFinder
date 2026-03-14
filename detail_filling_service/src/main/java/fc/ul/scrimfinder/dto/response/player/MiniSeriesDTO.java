package fc.ul.scrimfinder.dto.response.player;

public record MiniSeriesDTO(
        Integer losses,
        String progress,
        Integer target,
        Integer wins
) {
}
