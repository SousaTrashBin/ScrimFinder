package fc.ul.scrimfinder.dto.request;

import lombok.Data;

import java.util.Map;

@Data
public class MatchResultRequest {
    private String gameId;
    private Long queueId;
    private Map<Long, PlayerDelta> playerDeltas;
}
