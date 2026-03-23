package fc.ul.scrimfinder.dto.request;

import java.util.List;

public record PlayerDTO(String name, String tag, List<PlayerStatsDTO> playerMatchStats) {}
