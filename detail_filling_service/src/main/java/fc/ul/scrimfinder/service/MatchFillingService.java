package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.match.MatchStatsDTO;
import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;
import fc.ul.scrimfinder.exception.InvalidMatchFormatException;
import fc.ul.scrimfinder.exception.InvalidPlayerFormatException;
import fc.ul.scrimfinder.exception.InvalidTeamFormatException;
import fc.ul.scrimfinder.exception.MatchNotFoundException;
import fc.ul.scrimfinder.exception.UnauthorizedException;

public interface MatchFillingService {
    MatchStatsDTO getFilledMatch(String matchId) throws
            MatchNotFoundException,
            InvalidMatchFormatException,
            InvalidPlayerFormatException,
            InvalidTeamFormatException,
            UnauthorizedException,
            ExternalServiceUnavailableException;

    String getRawMatchData(String matchId) throws
            MatchNotFoundException,
            InvalidMatchFormatException,
            InvalidPlayerFormatException,
            InvalidTeamFormatException,
            UnauthorizedException,
            ExternalServiceUnavailableException;
}
