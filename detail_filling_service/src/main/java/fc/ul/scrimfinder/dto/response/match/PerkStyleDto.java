package fc.ul.scrimfinder.dto.response.match;

import java.util.List;

public record PerkStyleDto(
        String description,
        List<PerkStyleSelectionDto> selections,
        Integer style
) {
}
