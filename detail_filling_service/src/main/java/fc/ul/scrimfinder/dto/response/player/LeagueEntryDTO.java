package fc.ul.scrimfinder.dto.response.player;

public record LeagueEntryDTO(
        String leagueId,
        String puuid,
        String queueType,
        String tier,
        String rank,
        Integer leaguePoints,
        Integer wins,
        Integer losses,
        Boolean hotStreak,
        Boolean veteran,
        Boolean freshBlood,
        Boolean inactive,
        MiniSeriesDTO miniSeries
) {
}
