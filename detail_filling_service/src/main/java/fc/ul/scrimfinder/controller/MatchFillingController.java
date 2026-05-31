package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.dto.response.match.MatchStatsDTO;
import fc.ul.scrimfinder.service.MatchFillingService;
import fc.ul.scrimfinder.util.ErrorResponse;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/matches")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(
        name = "Match Filling",
        description = "Operations for retrieving match details from Riot's API")
public class MatchFillingController {

    @Inject MatchFillingService matchFillingService;

    @GET
    @Path("/{matchId}")
    @Operation(
            operationId = "getFilledMatch",
            summary = "Get simplified match information by Riot ID",
            description =
                    "You can extract Riot match IDs from LeagueOfGraphs match URLs. "
                            + "Example: https://www.leagueofgraphs.com/match/vn/1417849076#participant8 "
                            + "maps to VN_1417849076 (region prefix + '_' + match id).")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "200",
                        description = "Successfully retrieved the match details",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = MatchStatsDTO.class))),
                @APIResponse(
                        responseCode = "400",
                        description = "Invalid match ID provided",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "404",
                        description = "Match not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "408",
                        description = "Request timeout",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "500",
                        description = "Internal error communication with Riot - unexpected response format",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "503",
                        description = "Riot service unavailable",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "511",
                        description = "The server has an invalid/expired API key with Riot",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    @Timeout(10000)
    public Response getFilledMatch(
            @Parameter(description = "Riot match id in REGION_MATCHID format", example = "VN_1417849076")
                    @PathParam("matchId")
                    @NotBlank
                    String matchId) {
        MatchStatsDTO match = matchFillingService.getFilledMatch(matchId);
        return Response.ok(match).build();
    }

    @GET
    @Path("/{matchId}/raw")
    @Operation(
            operationId = "getRawMatchData",
            summary = "Get complete match information by Riot ID",
            description = "Accepts the same Riot match ID format, e.g. VN_1417849076.")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "200",
                        description = "Successfully retrieved the match details"),
                @APIResponse(
                        responseCode = "400",
                        description = "Invalid match ID provided",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "404",
                        description = "Match not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "408",
                        description = "Request timeout",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "500",
                        description = "Internal error communication with Riot - unexpected response format",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "503",
                        description = "Riot service unavailable",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "511",
                        description = "The server has an invalid/expired API key with Riot",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    @Timeout(10000)
    public Response getRawMatchData(
            @Parameter(description = "Riot match id in REGION_MATCHID format", example = "VN_1417849076")
                    @PathParam("matchId")
                    @NotBlank
                    String matchId) {
        String match = matchFillingService.getRawMatchData(matchId);
        return Response.ok(match).build();
    }
}
