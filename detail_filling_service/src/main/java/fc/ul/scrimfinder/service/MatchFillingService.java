package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.match.MatchStatsDTO;
import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;
import fc.ul.scrimfinder.exception.MatchNotFoundException;

public interface MatchFillingService {
    MatchStatsDTO getFilledMatch(Long matchId) throws MatchNotFoundException, ExternalServiceUnavailableException;

    String getRawMatchData(Long matchId) throws MatchNotFoundException, ExternalServiceUnavailableException;
}
