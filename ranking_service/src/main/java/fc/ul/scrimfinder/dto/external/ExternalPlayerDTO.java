package fc.ul.scrimfinder.dto.external;

import fc.ul.scrimfinder.dto.response.RankDTO;
import fc.ul.scrimfinder.util.GameMode;
import java.util.Map;

public record ExternalPlayerDTO(
        String lolAccountPPUID, String summonerName, String tag, Map<GameMode, RankDTO> rankDTOMap) {}
