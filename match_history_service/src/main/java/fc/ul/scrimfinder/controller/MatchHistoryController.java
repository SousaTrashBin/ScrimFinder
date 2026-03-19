package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.dto.request.MatchAddDTO;
import fc.ul.scrimfinder.dto.request.MatchStats;
import fc.ul.scrimfinder.dto.request.SortParam;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.service.MatchHistoryService;
import fc.ul.scrimfinder.util.ErrorResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
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
    @Path("/{matchId}")
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
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response getMatchById(@PathParam("matchId") @Positive Long matchId) {
        MatchDTO match = matchHistoryService.getMatchById(matchId);
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
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response getMatches(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") @Min(0) @Max(100) int size,
            @BeanParam @Valid MatchStats filterParams,
            @QueryParam("sort") List<SortParam> sortParams) {
        PaginatedResponseDTO<MatchDTO> matches =
                matchHistoryService.getMatches(page, size, filterParams, sortParams);
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
                        description = "Invalid request payload",
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
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response addMatchById(
            @PathParam("riotMatchId") @NotBlank String riotMatchId,
            java.util.Map<Long, Integer> playerMMRGains) {
        MatchDTO addedMatch = matchHistoryService.addMatchById(riotMatchId, playerMMRGains);
        return Response.status(Response.Status.CREATED).entity(addedMatch).build();
    }

    @POST
    @Operation(summary = "Add a finished match to history with the given properties (Internal)")
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
                        description = "Invalid request payload",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response addMatch(@Valid MatchAddDTO match) {
        MatchDTO addedMatch = matchHistoryService.addMatch(match);
        return Response.status(Response.Status.CREATED).entity(addedMatch).build();
    }

    @DELETE
    @Path("/{matchId}")
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
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response deleteMatchById(@PathParam("matchId") @Positive Long matchId) {
        MatchDTO deletedMatch = matchHistoryService.deleteMatchById(matchId);
        return Response.ok(deletedMatch).build();
    }
}
