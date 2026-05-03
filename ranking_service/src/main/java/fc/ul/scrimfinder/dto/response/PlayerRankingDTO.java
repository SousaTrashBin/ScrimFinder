package fc.ul.scrimfinder.dto.response;

import fc.ul.scrimfinder.util.Region;
import java.util.UUID;

public record PlayerRankingDTO(
        UUID id,
        UUID playerId,
        String discordUsername,
        String lolAccountPPUID,
        Region region,
        UUID queueId,
        int mmr,
        int wins,
        int losses) {}
