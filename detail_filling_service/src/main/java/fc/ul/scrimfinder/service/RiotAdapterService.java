package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.match.MatchStatsDTO;
import fc.ul.scrimfinder.dto.response.player.PlayerDTO;
import fc.ul.scrimfinder.exception.*;
import fc.ul.scrimfinder.util.Subregion;

public interface RiotAdapterService {
    String getRawMatchData(String matchId, Subregion subregion) throws
            MatchNotFoundException,
            InvalidMatchFormatException,
            InvalidPlayerFormatException,
            InvalidTeamFormatException,
            UnauthorizedException,
            ExternalServiceUnavailableException;

    MatchStatsDTO getMatchData(String matchId, Subregion subregion) throws
            MatchNotFoundException,
            InvalidMatchFormatException,
            InvalidPlayerFormatException,
            InvalidTeamFormatException,
            UnauthorizedException,
            ExternalServiceUnavailableException;

    PlayerDTO getPlayerData(String name, String tag) throws
            PlayerNotFoundException,
            InvalidPlayerFormatException,
            UnauthorizedException,
            ExternalServiceUnavailableException;
}
