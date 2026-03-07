package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.dto.request.CreatePlayerRequest;
import fc.ul.scrimfinder.dto.request.UpdateMMRRequest;
import fc.ul.scrimfinder.service.PlayerRankingService;
import io.smallrye.common.constraint.NotNull;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Optional;

@Path("/player")
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

    @PUT
    @Path("/{playerId}/mmr")
    @Operation(summary = "Update MMR after a match is done")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "MMR successfully updated"),
            @APIResponse(responseCode = "400", description = "Invalid request payload or negative playerId"),
            @APIResponse(responseCode = "404", description = "Player or Queue not found")
    })
    public Response updatePlayerMMR(@PathParam("playerId") @Positive Long playerId,
                                    @NotNull @Valid UpdateMMRRequest updateMMRRequest) {
        var updatedMMR = playerRankingService.updatePlayerMMR(playerId, updateMMRRequest);
        return Response.ok(updatedMMR).build();
    }

    @POST
    @Path("/{playerId}/mmr")
    @Operation(summary = "Populates initial player MMR according to a pre-determined rule")
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "Initial MMR successfully created"),
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