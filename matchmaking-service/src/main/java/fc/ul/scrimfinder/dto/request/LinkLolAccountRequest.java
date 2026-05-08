package fc.ul.scrimfinder.dto.request;

import fc.ul.scrimfinder.util.Region;

public record LinkLolAccountRequest(String puuid, String gameName, String tagLine, Region region) {}
