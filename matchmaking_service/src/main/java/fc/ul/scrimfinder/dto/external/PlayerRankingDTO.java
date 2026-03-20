package fc.ul.scrimfinder.dto.external;

import fc.ul.scrimfinder.util.Region;
import java.util.UUID;

public record PlayerRankingDTO(
        UUID publicId,
        Long playerId,
        String username,
        String lolAccountPPUID,
        Region region,
        Long queueId,
        int mmr,
        int wins,
        int losses) {}
