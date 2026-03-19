package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.match.MatchStatsDTO;
import fc.ul.scrimfinder.exception.*;
import fc.ul.scrimfinder.util.Subregion;

public interface MatchFillingService {
    MatchStatsDTO getFilledMatch(String matchId, Subregion subregion) throws
            MatchNotFoundException,
            InvalidMatchFormatException,
            InvalidPlayerFormatException,
            InvalidTeamFormatException,
            UnauthorizedException,
            ExternalServiceUnavailableException;

    String getRawMatchData(String matchId, Subregion subregion) throws
            MatchNotFoundException,
            InvalidMatchFormatException,
            InvalidPlayerFormatException,
            InvalidTeamFormatException,
            UnauthorizedException,
            ExternalServiceUnavailableException;
}
