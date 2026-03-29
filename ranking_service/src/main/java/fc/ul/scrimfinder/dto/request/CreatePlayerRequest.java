package fc.ul.scrimfinder.dto.request;

import java.util.Optional;
import java.util.UUID;

public record CreatePlayerRequest(Optional<UUID> queueId) {}
