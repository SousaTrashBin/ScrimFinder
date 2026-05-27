package fc.ul.scrimfinder.service;

import com.fasterxml.jackson.databind.JsonNode;
import fc.ul.scrimfinder.dto.request.PlayerStatsDTO;
import fc.ul.scrimfinder.dto.request.TeamStatsDTO;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;
import fc.ul.scrimfinder.exception.InvalidExternalJsonFormatException;

public interface DetailFillingAdapterService {
    MatchDTO getMatch(String riotMatchId)
            throws InvalidExternalJsonFormatException, ExternalServiceUnavailableException;

    MatchDTO mapToMatchFromDetailFilling(JsonNode match) throws InvalidExternalJsonFormatException;

    PlayerStatsDTO mapToPlayerFromDetailFilling(JsonNode player, Long gameDuration)
            throws InvalidExternalJsonFormatException;

    TeamStatsDTO mapToTeamFromDetailFilling(JsonNode team) throws InvalidExternalJsonFormatException;
}
