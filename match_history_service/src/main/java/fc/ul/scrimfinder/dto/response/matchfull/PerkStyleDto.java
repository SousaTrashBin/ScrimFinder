package fc.ul.scrimfinder.dto.response.matchfull;

import java.util.List;

public record PerkStyleDto(
        String description,
        List<PerkStyleSelectionDto> selections,
        Integer style
) {
}
