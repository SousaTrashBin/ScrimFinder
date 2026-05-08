package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.dto.request.LinkLolAccountRequest;
import fc.ul.scrimfinder.dto.request.SetPrimaryAccountRequest;
import fc.ul.scrimfinder.dto.response.PlayerDTO;
import fc.ul.scrimfinder.dto.response.RiotAccountDTO;
import fc.ul.scrimfinder.service.PlayerService;
import fc.ul.scrimfinder.util.ErrorResponse;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@Tag(
        name = "Player Management",
        description = "Operations for managing player profiles and external account links")
public class PlayerController {

    @Inject PlayerService playerService;

    @GET
    @Path("/{playerId}")
    @Operation(summary = "Get player details")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "200",
                        description = "Player found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = PlayerDTO.class))),
                @APIResponse(
                        responseCode = "404",
                        description = "Player not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response getPlayer(@PathParam("playerId") @NotNull UUID playerId) {
        var player = playerService.getPlayer(playerId);
        return Response.ok(player).build();
    }

    @GET
    @Path("/{playerId}/primary-account")
    @Operation(summary = "Get the primary linked Riot account for a player")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "200",
                        description = "Primary account found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = RiotAccountDTO.class))),
                @APIResponse(
                        responseCode = "400",
                        description = "No League of Legends account linked",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "404",
                        description = "Player not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response getPrimaryAccount(@PathParam("playerId") @NotNull UUID playerId) {
        RiotAccountDTO account = playerService.getPrimaryAccount(playerId);
        return Response.ok(account).build();
    }

    @PUT
    @Path("/{playerId}/link-lol-account")
    @Operation(summary = "Link a League of Legends account to a player")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "200",
                        description = "Account successfully linked",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = PlayerDTO.class))),
                @APIResponse(
                        responseCode = "400",
                        description = "Invalid account info or already linked",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "404",
                        description = "Player not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response linkLolAccount(
            @PathParam("playerId") @NotNull UUID playerId,
            @Valid @NotNull LinkLolAccountRequest request) {
        var updatedPlayer =
                playerService.linkLolAccount(
                        playerId, request.puuid(), request.gameName(), request.tagLine(), request.region());
        return Response.ok(updatedPlayer).build();
    }

    @PUT
    @Path("/{playerId}/set-primary-account")
    @Operation(summary = "Set one of the linked Riot accounts as the primary account")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "200",
                        description = "Primary account successfully updated",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = PlayerDTO.class))),
                @APIResponse(
                        responseCode = "404",
                        description = "Player or Riot account not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response setPrimaryAccount(
            @PathParam("playerId") @NotNull UUID playerId,
            @Valid @NotNull SetPrimaryAccountRequest request) {
        var updatedPlayer = playerService.setPrimaryAccount(playerId, request.puuid());
        return Response.ok(updatedPlayer).build();
    }

    @POST
    @Path("/{playerId}/sync-mmr")
    @Operation(summary = "Manually fetch and sync the player's MMR from the external LoL service")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "200",
                        description = "MMR successfully synced",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = PlayerDTO.class))),
                @APIResponse(
                        responseCode = "400",
                        description = "League of Legends account not linked",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "404",
                        description = "Player not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response syncExternalMMR(@PathParam("playerId") @NotNull UUID playerId) {
        var updatedRanking = playerService.syncPlayerMMR(playerId);
        return Response.ok(updatedRanking).build();
    }

    @DELETE
    @Path("/links/{gameName}/{tagLine}")
    @Operation(summary = "Unlink a League of Legends account")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "204",
                        description = "Account successfully unlinked or not found")
            })
    public Response unlinkLolAccount(
            @PathParam("gameName") @NotBlank String gameName,
            @PathParam("tagLine") @NotBlank String tagLine) {
        playerService.unlinkLolAccount(gameName, tagLine);
        return Response.noContent().build();
    }
}
