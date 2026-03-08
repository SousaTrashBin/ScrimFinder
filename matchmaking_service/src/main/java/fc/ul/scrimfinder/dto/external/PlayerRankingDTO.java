package fc.ul.scrimfinder.dto.external;

import lombok.Data;

@Data
public class PlayerRankingDTO {
    private Long playerId;
    private Long queueId;
    private int mmr;
    private String lolAccountPPUID;
}
