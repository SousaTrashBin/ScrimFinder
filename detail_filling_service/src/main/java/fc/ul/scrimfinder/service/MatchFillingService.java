package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.match.MatchStatsDTO;
import fc.ul.scrimfinder.exception.*;

public interface MatchFillingService {
    MatchStatsDTO getFilledMatch(String matchId)
            throws MatchNotFoundException,
            InvalidMatchFormatException,
            InvalidPlayerFormatException,
            InvalidTeamFormatException,
            UnauthorizedException,
            ExternalServiceUnavailableException;

    String getRawMatchData(String matchId)
            throws MatchNotFoundException,
            InvalidMatchFormatException,
            InvalidPlayerFormatException,
            InvalidTeamFormatException,
            UnauthorizedException,
            ExternalServiceUnavailableException;
}
