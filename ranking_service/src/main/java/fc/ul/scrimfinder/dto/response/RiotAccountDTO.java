package fc.ul.scrimfinder.dto.response;

import fc.ul.scrimfinder.util.Region;

public record RiotAccountDTO(
        Long id, String puuid, String gameName, String tagLine, Region region, boolean isPrimary) {}
