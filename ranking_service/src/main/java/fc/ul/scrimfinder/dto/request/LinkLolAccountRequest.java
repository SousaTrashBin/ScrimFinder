package fc.ul.scrimfinder.dto.request;

import fc.ul.scrimfinder.util.Region;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LinkLolAccountRequest(
        String puuid, @NotBlank String gameName, @NotBlank String tagLine, @NotNull Region region) {}
