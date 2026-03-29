package fc.ul.scrimfinder.dto.response;

import fc.ul.scrimfinder.util.Region;
import java.util.UUID;

public record RiotAccountDTO(
        UUID id, String puuid, String gameName, String tagLine, Region region, boolean isPrimary) {}
