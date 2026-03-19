package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.match.MatchDTO;
import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;
import fc.ul.scrimfinder.exception.MatchNotFoundException;

public interface MatchFillingService {
    MatchDTO getFilledMatch(Long matchId)
            throws MatchNotFoundException, ExternalServiceUnavailableException;

    String getRawMatchData(Long matchId)
            throws MatchNotFoundException, ExternalServiceUnavailableException;
}
