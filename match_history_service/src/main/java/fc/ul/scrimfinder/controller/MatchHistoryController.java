package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.domain.Team;
import fc.ul.scrimfinder.dto.request.MatchAddDto;
import fc.ul.scrimfinder.dto.request.MatchStats;
import fc.ul.scrimfinder.dto.request.TeamStats;
import fc.ul.scrimfinder.dto.response.MatchFullDto;
import fc.ul.scrimfinder.dto.response.MatchSimplifiedDto;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDto;
import fc.ul.scrimfinder.service.MatchHistoryService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/matches")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Match History", description = "Allows for the visualization of all LOL matches filtered and sorted by the given parameters")
public class MatchHistoryController {

    @Inject
    MatchHistoryService matchHistoryService;

    @GET
    @Path("/{matchId}")
    @Operation(summary = "Get complete match information based on its id if the information retrieved by the match filtering is not enough")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Successfully retrieved the match"),
            @APIResponse(responseCode = "400", description = "Negative match ID"),
            @APIResponse(responseCode = "404", description = "Match not found")
    })
    public Response getMatchById(@PathParam("matchId") @Positive Long matchId) {
        MatchFullDto match = matchHistoryService.getMatchById(matchId);
        return Response.ok(match).build();
    }

    @GET
    @Operation(summary = "Get simplified information about the matches, filtered and sorted based on the filled parameters")
    @APIResponses(value= {
            @APIResponse(responseCode = "200", description = "Successfully retrieved the matches"),
            @APIResponse(responseCode = "400", description = "Invalid match query parameters or pagination parameters (e.g., size < 0 or > 100)")
    })
    public Response getMatches(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") @Min(0) @Max(100) int size,
            @BeanParam @Valid MatchStats params
            ) {
        PaginatedResponseDto<MatchSimplifiedDto> matches = matchHistoryService.getMatches(page, size, params);
        return Response.ok(matches).build();
    }

    @POST
    @Operation(summary = "Adds the most important stats of a finished match")
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "Match successfully added"),
            @APIResponse(responseCode = "400", description = "Invalid request payload")
    })
    public Response addMatch(@Valid MatchAddDto match) {
        MatchSimplifiedDto addedMatch = matchHistoryService.addMatch(match);
        return Response.ok(addedMatch).build();
    }
}
