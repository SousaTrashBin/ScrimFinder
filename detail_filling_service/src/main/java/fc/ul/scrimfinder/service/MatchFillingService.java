package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.match.MatchDto;
import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;
import fc.ul.scrimfinder.exception.MatchNotFoundException;

public interface MatchFillingService {
    MatchDto getMatchById(Long matchId) throws MatchNotFoundException, ExternalServiceUnavailableException;

    String getRawMatchById(Long matchId) throws MatchNotFoundException, ExternalServiceUnavailableException;
}
