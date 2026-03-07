package fc.ul.scrimfinder.dto.request;

import java.util.Optional;

public record UpdateMMRRequest(int delta, Optional<Long> queueId) {
}
