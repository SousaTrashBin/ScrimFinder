package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.request.filtering.MatchFilters;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.exception.*;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;

public interface MatchHistoryService {
    @CircuitBreaker(skipOn = {MatchNotFoundException.class})
    MatchDTO getMatchById(String riotMatchId)
            throws MatchNotFoundException,
                    InvalidExternalJsonFormatException,
                    ExternalServiceUnavailableException;

    @CircuitBreaker(skipOn = {InvalidIntervalException.class, InvalidTeamsException.class})
    PaginatedResponseDTO<MatchDTO> getMatches(int page, int size, MatchFilters filterParams)
            throws InvalidIntervalException, InvalidTeamsException;

    @CircuitBreaker(
            skipOn = {
                MatchAlreadyExistsException.class,
                MatchNotFoundException.class,
                NotEnoughMMRDeltasException.class,
                PlayerNotFoundException.class
            })
    MatchDTO addMatchById(String riotMatchId, UUID queueId, Map<String, Integer> mmrDeltas)
            throws MatchAlreadyExistsException,
                    MatchNotFoundException,
                    NotEnoughMMRDeltasException,
                    PlayerNotFoundException;

    @CircuitBreaker(skipOn = {MatchNotFoundException.class})
    MatchDTO deleteMatchById(String riotMatchId) throws MatchNotFoundException;
}
