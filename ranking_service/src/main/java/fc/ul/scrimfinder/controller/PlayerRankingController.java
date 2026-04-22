package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.dto.request.CreatePlayerRequest;
import fc.ul.scrimfinder.dto.request.MatchResultRequest;
import fc.ul.scrimfinder.dto.response.PlayerRankingDTO;
import fc.ul.scrimfinder.exception.PlayerNotFoundException;
import fc.ul.scrimfinder.service.PlayerRankingService;
import fc.ul.scrimfinder.util.ErrorResponse;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.constraint.NotNull;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;
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
    @Path("/{playerId}/queue")
    @Operation(
            summary = "Get ranking information for a player",
            description =
                    "Returns a single ranking if queueId is provided, or a list of all rankings if omitted.")
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
    public Response getPlayerRanking(
            @PathParam("playerId") UUID playerId, @QueryParam("queueId") Optional<UUID> queueId) {
        var rankings = playerRankingService.getPlayerRanking(playerId, queueId);

        if (queueId.isPresent() && rankings.isEmpty()) {
            throw new PlayerNotFoundException(
                    "Ranking not found for player " + playerId + " in queue " + queueId.get());
        }

        return Response.ok(rankings).build();
    }

    @POST
    @Path("/results")
    @Operation(summary = "Process match results and update rankings (Internal)")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "200",
                        description = "Rankings successfully updated",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = PlayerRankingDTO.class))),
                @APIResponse(
                        responseCode = "400",
                        description = "Invalid request data",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "404",
                        description = "Queue not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response processMatchResults(@NotNull @Valid MatchResultRequest matchResultRequest) {
        var updatedRankings = playerRankingService.processMatchResults(matchResultRequest);
        return Response.ok(updatedRankings).build();
    }

    @POST
    @Path("/{playerId}/mmr")
    @Operation(summary = "Initialize MMR for a player in a specific queue")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "201",
                        description = "Initial MMR successfully created",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = PlayerRankingDTO.class))),
                @APIResponse(
                        responseCode = "400",
                        description = "Invalid request payload",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "404",
                        description = "Player or Queue not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "409",
                        description = "MMR already exists for this player in this queue",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response populatePlayerMMR(
            @PathParam("playerId") UUID playerId,
            @NotNull @Valid CreatePlayerRequest createPlayerRequest) {
        var populatedMMR = playerRankingService.populatePlayerMMR(playerId, createPlayerRequest);
        return Response.status(Response.Status.CREATED).entity(populatedMMR).build();
    }
}
