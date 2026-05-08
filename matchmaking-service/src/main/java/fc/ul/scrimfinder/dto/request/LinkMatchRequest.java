package fc.ul.scrimfinder.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LinkMatchRequest(@NotBlank String externalGameId) {}
