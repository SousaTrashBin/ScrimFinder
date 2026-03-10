package fc.ul.scrimfinder.dto.response.match;

import java.util.List;

public record InfoDto(
        String endOfGameResult,
        Long gameCreation,
        Long gameDuration,
        Long gameEndTimeStamp,
        Long gameId,
        String gameMode,
        String gameName,
        Long gameStartTimeStamp,
        String gameType,
        String gameVersion,
        Integer mapId,
        List<ParticipantDto> participants,
        String platformId,
        Integer queueId,
        List<TeamDto> teams,
        String tournamentCode
) {
}
