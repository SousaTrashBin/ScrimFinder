package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.match.MatchStatsDTO;
import fc.ul.scrimfinder.exception.*;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;

public interface MatchFillingService {
    @CircuitBreaker(skipOn = {MatchNotFoundException.class})
    MatchStatsDTO getFilledMatch(String matchId)
            throws MatchNotFoundException,
                    InvalidMatchFormatException,
                    InvalidPlayerFormatException,
                    InvalidTeamFormatException,
                    UnauthorizedException,
                    ExternalServiceUnavailableException;

    @CircuitBreaker(skipOn = {MatchNotFoundException.class})
    String getRawMatchData(String matchId)
            throws MatchNotFoundException,
                    InvalidMatchFormatException,
                    InvalidPlayerFormatException,
                    InvalidTeamFormatException,
                    UnauthorizedException,
                    ExternalServiceUnavailableException;
}
