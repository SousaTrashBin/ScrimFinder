package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.dto.response.PlayerRankingDTO;
import fc.ul.scrimfinder.exception.PlayerRankingNotFoundException;
import fc.ul.scrimfinder.service.PlayerRankingService;
import fc.ul.scrimfinder.util.ErrorResponse;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/players")
@Blocking
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Player Ranking", description = "Operations for player MMR and ranking history")
public class PlayerRankingController {

    @Inject PlayerRankingService playerRankingService;

    @GET
    @Path("/{playerId}/queue-rankings")
    @Operation(summary = "Get all queue rankings for a player")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "200",
                        description = "Successfully retrieved player ranking(s)",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = PlayerRankingDTO.class))),
                @APIResponse(
                        responseCode = "404",
                        description = "Player ranking not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response getPlayerRankings(@PathParam("playerId") UUID playerId) {
        var rankings = playerRankingService.getPlayerRanking(playerId, java.util.Optional.empty());
        return Response.ok(rankings).build();
    }

    @GET
    @Path("/{playerId}/queue-rankings/{queueId}")
    @Operation(summary = "Get a player's ranking for a specific queue")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "200",
                        description = "Successfully retrieved player queue ranking",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = PlayerRankingDTO.class))),
                @APIResponse(
                        responseCode = "404",
                        description = "Player ranking not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response getPlayerRankingByQueue(
            @PathParam("playerId") UUID playerId, @PathParam("queueId") UUID queueId) {
        var rankings = playerRankingService.getPlayerRanking(playerId, java.util.Optional.of(queueId));

        if (rankings.isEmpty()) {
            throw new PlayerRankingNotFoundException(
                    "Ranking not found for player " + playerId + " in queue " + queueId);
        }

        return Response.ok(rankings).build();
    }
}
