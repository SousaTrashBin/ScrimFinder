package fc.ul.scrimfinder.dto.response;

import java.util.UUID;

public record PlayerRankingDTO(
        UUID publicId,
        Long playerId,
        String username,
        String lolAccountPPUID,
        Long queueId,
        int mmr,
        int wins,
        int losses
) {
}
