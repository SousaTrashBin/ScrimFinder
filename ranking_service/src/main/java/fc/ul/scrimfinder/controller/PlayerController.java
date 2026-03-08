package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.dto.response.PlayerDTO;
import fc.ul.scrimfinder.dto.response.PlayerRankingDTO;
import fc.ul.scrimfinder.service.PlayerService;
import fc.ul.scrimfinder.util.ErrorResponse;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

@Path("/players")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Player Management", description = "Operations for managing player profiles and external account links")
public class PlayerController {

    @Inject
    PlayerService playerService;


    @POST
    @Operation(summary = "Register a new player in the system (Internal)")
    @APIResponses(value = {
            @APIResponse(responseCode = "201", description = "Player successfully created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlayerDTO.class))),
            @APIResponse(responseCode = "409", description = "Username already exists",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response createPlayer(
            @QueryParam("id") @NotNull Long id,
            @QueryParam("username") @NotBlank String username) {
        var player = playerService.createPlayer(id, username);
        return Response.status(Response.Status.CREATED).entity(player).build();
    }

    @PUT
    @Path("/{playerId}/link-lol-account")
    @Operation(summary = "Link a League of Legends account ID to a player")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Account successfully linked",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlayerDTO.class))),
            @APIResponse(responseCode = "400", description = "Invalid League of Legends account ID or already linked",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = "404", description = "Player not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response linkLolAccount(
            @PathParam("playerId") @Positive Long playerId,
            @QueryParam("lolAccountId") @NotBlank String lolAccountId) {
        var updatedPlayer = playerService.linkLolAccount(playerId, lolAccountId);
        return Response.ok(updatedPlayer).build();
    }

    @POST
    @Path("/{playerId}/sync-mmr")
    @Operation(summary = "Manually fetch and sync the player's MMR from the external LoL service")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "MMR successfully synced",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlayerRankingDTO.class))),
            @APIResponse(responseCode = "400", description = "League of Legends account not linked",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = "404", description = "Player not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response syncExternalMMR(@PathParam("playerId") @Positive Long playerId) {
        var updatedRanking = playerService.syncPlayerMMR(playerId);
        return Response.ok(updatedRanking).build();
    }
}