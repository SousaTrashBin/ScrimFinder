package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.dto.response.PlayerDTO;
import fc.ul.scrimfinder.service.PlayerService;
import fc.ul.scrimfinder.util.ErrorResponse;
import fc.ul.scrimfinder.util.Region;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
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

    @POST
    @Operation(summary = "Register a new player in the system (Internal)")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "201",
                        description = "Player successfully created",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = PlayerDTO.class))),
                @APIResponse(
                        responseCode = "409",
                        description = "Username already exists",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response createPlayer(
            @QueryParam("id") @NotNull UUID id, @QueryParam("username") @NotBlank String username) {
        var player = playerService.createPlayer(id, username);
        return Response.status(Response.Status.CREATED).entity(player).build();
    }

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
            @QueryParam("puuid") String puuid,
            @QueryParam("gameName") @NotBlank String gameName,
            @QueryParam("tagLine") @NotBlank String tagLine,
            @QueryParam("region") @NotNull Region region) {
        var updatedPlayer = playerService.linkLolAccount(playerId, puuid, gameName, tagLine, region);
        return Response.ok(updatedPlayer).build();
    }

    @POST
    @Path("/{playerId}/link")
    @Operation(summary = "Link a League of Legends account to a player (Alias for /link-lol-account)")
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
    public Response linkLolAccountAlias(
            @PathParam("playerId") @NotNull UUID playerId,
            @QueryParam("puuid") String puuid,
            @QueryParam("gameName") @NotBlank String gameName,
            @QueryParam("tagLine") @NotBlank String tagLine,
            @QueryParam("region") @NotNull Region region) {
        return linkLolAccount(playerId, puuid, gameName, tagLine, region);
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
            @PathParam("playerId") @NotNull UUID playerId, @QueryParam("puuid") @NotBlank String puuid) {
        var updatedPlayer = playerService.setPrimaryAccount(playerId, puuid);
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
    @Path("/link")
    @Operation(summary = "Unlink a League of Legends account using gameName and tagLine")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "204",
                        description = "Account successfully unlinked or not found")
            })
    public Response unlinkLolAccount(
            @QueryParam("gameName") @NotBlank String gameName,
            @QueryParam("tagLine") @NotBlank String tagLine) {
        playerService.unlinkLolAccount(gameName, tagLine);
        return Response.noContent().build();
    }
}
