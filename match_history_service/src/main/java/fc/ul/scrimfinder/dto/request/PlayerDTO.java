package fc.ul.scrimfinder.dto.request;

import java.util.List;

public record PlayerDTO(
        String puuid, String name, String tag, List<PlayerStatsDTO> playerMatchStats) {}
