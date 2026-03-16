package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.match.MatchStatsDTO;
import fc.ul.scrimfinder.dto.response.player.PlayerDTO;
import fc.ul.scrimfinder.exception.*;

public interface RiotAdapterService {
    String getRawMatchData(String matchId) throws
            MatchNotFoundException,
            InvalidMatchFormatException,
            InvalidPlayerFormatException,
            InvalidTeamFormatException,
            ExternalServiceUnavailableException;

    MatchStatsDTO getMatchData(String matchId) throws
            MatchNotFoundException,
            InvalidMatchFormatException,
            InvalidPlayerFormatException,
            InvalidTeamFormatException,
            ExternalServiceUnavailableException;

    PlayerDTO getPlayerData(String name, String tag) throws ExternalServiceUnavailableException;
}
