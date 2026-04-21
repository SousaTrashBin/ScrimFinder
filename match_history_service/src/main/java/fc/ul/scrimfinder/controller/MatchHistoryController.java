package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.dto.request.filtering.MatchFilters;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.service.MatchHistoryService;
import fc.ul.scrimfinder.util.ErrorResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/matches")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(
        name = "Match History",
        description = "Operations for retrieving and managing League of Legends match history")
public class MatchHistoryController {

    @Inject MatchHistoryService matchHistoryService;

    @GET
    @Path("/{riotMatchId}")
    @Operation(summary = "Get simplified match information by ID")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "200",
                        description = "Successfully retrieved the match details",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = MatchDTO.class))),
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
                        description = "Internal error in communicating with other services",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "503",
                        description = "Some of the needed servers cannot process requests",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
            })
    @Timeout(10000)
    public Response getMatchById(@PathParam("riotMatchId") @NotBlank String riotMatchId) {
        MatchDTO match = matchHistoryService.getMatchById(riotMatchId);
        return Response.ok(match).build();
    }

    @GET
    @Operation(summary = "Get paginated match history with filters and sorting")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "200",
                        description = "Successfully retrieved filtered matches",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = PaginatedResponseDTO.class))),
                @APIResponse(
                        responseCode = "400",
                        description = "Invalid query or pagination parameters",
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
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    @Timeout(30000)
    public Response getMatches(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size,
            @Valid MatchFilters filterParams) {
        PaginatedResponseDTO<MatchDTO> matches =
                matchHistoryService.getMatches(page, size, filterParams);
        return Response.ok(matches).build();
    }

    @POST
    @Path("/{riotMatchId}")
    @Operation(
            summary = "Add a finished match to history based on the corresponding match in Riot's API")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "201",
                        description = "Match history successfully created",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = MatchDTO.class))),
                @APIResponse(
                        responseCode = "400",
                        description = "Match already exists in the history",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "404",
                        description =
                                "Match not found or player not found in the match for the provided MMR delta",
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
            })
    @Timeout(15000)
    public Response addMatchById(
            @PathParam("riotMatchId") @NotBlank String riotMatchId,
            @QueryParam("queueId") @NotNull UUID queueId,
            @Size(min = 10, max = 10, message = "The match must have 10 players")
                    Map<String, Integer> playerMMRGains) {
        MatchDTO addedMatch = matchHistoryService.addMatchById(riotMatchId, queueId, playerMMRGains);
        return Response.status(Response.Status.CREATED).entity(addedMatch).build();
    }

    @DELETE
    @Path("/{riotMatchId}")
    @Operation(summary = "Delete a match from history (Internal)")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "200",
                        description = "Match history successfully deleted",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = MatchDTO.class))),
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
            })
    @Timeout(10000)
    public Response deleteMatchById(@PathParam("riotMatchId") @NotBlank String riotMatchId) {
        MatchDTO deletedMatch = matchHistoryService.deleteMatchById(riotMatchId);
        return Response.ok(deletedMatch).build();
    }
}
