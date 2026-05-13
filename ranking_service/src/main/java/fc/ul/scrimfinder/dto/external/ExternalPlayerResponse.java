package fc.ul.scrimfinder.dto.external;

import java.util.List;

public record ExternalPlayerResponse(
        Account account, Region region, Summoner summoner, List<Queue> queues) {
    public record Account(String puuid, String name, String tag) {}

    public record Region(String region, String subregion) {}

    public record Summoner(Integer icon, Integer level) {}

    public record Queue(
            String queueType, Rank rank, Integer wins, Integer losses, Boolean hotStreak) {}

    public record Rank(String tier, Integer division, Integer lps) {}
}
