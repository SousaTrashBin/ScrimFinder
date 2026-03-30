package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;
import fc.ul.scrimfinder.exception.InvalidExternalJsonFormatException;
import fc.ul.scrimfinder.exception.PlayerNotFoundException;

public interface DetailFillingAdapterService {
    MatchDTO getMatch(String riotMatchId)
            throws InvalidExternalJsonFormatException, ExternalServiceUnavailableException;

    String getPlayerPuuid(String server, String name, String tag)
            throws PlayerNotFoundException,
                    InvalidExternalJsonFormatException,
                    ExternalServiceUnavailableException;

    Integer getPlayerIcon(String server, String name, String tag)
            throws PlayerNotFoundException,
                    InvalidExternalJsonFormatException,
                    ExternalServiceUnavailableException;
}
