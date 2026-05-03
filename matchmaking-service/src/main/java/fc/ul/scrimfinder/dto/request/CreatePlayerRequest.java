package fc.ul.scrimfinder.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreatePlayerRequest(@NotNull UUID id, @NotBlank String discordUsername) {}
