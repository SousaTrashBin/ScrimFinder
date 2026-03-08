package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.dto.request.CreatePlayerRequest;
import fc.ul.scrimfinder.dto.request.MatchResultRequest;
import fc.ul.scrimfinder.dto.response.PlayerRankingDTO;
import fc.ul.scrimfinder.service.PlayerRankingService;
import io.smallrye.common.constraint.NotNull;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Optional;

@Path("/players")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Player Ranking", description = "Player MMR and ranking operations")
public class PlayerRankingController {

    @Inject
    PlayerRankingService playerRankingService;

    @GET
    @Path("/{playerId}/queue")
    @Operation(summary = "Get complete ranking information for a player")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Successfully retrieved player ranking"),
            @APIResponse(responseCode = "404", description = "Player or Queue not found")
    })
    public Response getPlayerRanking(@PathParam("playerId") Long playerId,
                                     @QueryParam("queueId") Optional<Long> queueId) {
        var ranking = playerRankingService.getPlayerRanking(playerId, queueId);
        return Response.ok(ranking).build();
    }

    @POST
    @Path("/matches/results")
    @Operation(summary = "Batch update MMR for all players after a match completion",
            description = "Processes match results including MMR deltas and winners.")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Match results processed successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(type = SchemaType.OBJECT))),
            @APIResponse(responseCode = "400", description = "Invalid request payload"),
            @APIResponse(responseCode = "404", description = "One or more players/queue not found")
    })
    public Response processMatchResults(@NotNull @Valid MatchResultRequest matchResultRequest) {
        var updatedRankings = playerRankingService.processMatchResults(matchResultRequest);
        return Response.ok(updatedRankings).build();
    }

    @POST
    @Path("/{playerId}/mmr")
    @Operation(summary = "Populates initial player MMR according to a pre-determined rule")
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "Initial MMR successfully created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlayerRankingDTO.class))),
            @APIResponse(responseCode = "400", description = "Invalid request payload"),
            @APIResponse(responseCode = "404", description = "Player or Queue not found"),
            @APIResponse(responseCode = "409", description = "MMR already exists for this player in this queue")
    })
    public Response populatePlayerMMR(@PathParam("playerId") Long playerId,
                                      @NotNull @Valid CreatePlayerRequest createPlayerRequest) {
        var populatedMMR = playerRankingService.populatePlayerMMR(playerId, createPlayerRequest);
        return Response.status(Response.Status.CREATED).entity(populatedMMR).build();
    }
}
