package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;
import fc.ul.scrimfinder.exception.InvalidExternalJsonFormatException;

public interface DetailFillingAdapterService {
    MatchDTO getMatch(String riotMatchId)
            throws InvalidExternalJsonFormatException, ExternalServiceUnavailableException;
}
