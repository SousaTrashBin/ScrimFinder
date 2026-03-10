package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.dto.response.match.MatchDto;
import fc.ul.scrimfinder.service.MatchFillingService;
import fc.ul.scrimfinder.util.ErrorResponse;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/matches")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Match Filling", description = "Operations for retrieving match details from Riot's API")
public class MatchFillingController {

    @Inject
    MatchFillingService matchFillingService;

    @GET
    @Path("/{matchId}")
    @Operation(summary = "Get simplified match information by Riot ID")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Successfully retrieved the match details",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = MatchDto.class))),
            @APIResponse(responseCode = "400", description = "Invalid match ID provided",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = "404", description = "Match not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getMatchById(@PathParam("matchId") @Positive Long matchId) {
        MatchDto match = matchFillingService.getMatchById(matchId);
        return Response.ok(match).build();
    }

    @GET
    @Path("/{matchId}/raw")
    @Operation(summary = "Get complete match information by Riot ID")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Successfully retrieved the match details"),
            @APIResponse(responseCode = "400", description = "Invalid match ID provided",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = "404", description = "Match not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getRawMatchById(@PathParam("matchId") @Positive Long matchId) {
        String match = matchFillingService.getRawMatchById(matchId);
        return Response.ok(match).build();
    }
}
