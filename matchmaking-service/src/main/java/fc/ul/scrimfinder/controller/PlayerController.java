package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.dto.request.CreatePlayerRequest;
import fc.ul.scrimfinder.dto.response.LobbyDTO;
import fc.ul.scrimfinder.dto.response.MatchTicketDTO;
import fc.ul.scrimfinder.dto.response.PlayerDTO;
import fc.ul.scrimfinder.service.MatchmakingService;
import fc.ul.scrimfinder.service.PlayerService;
import fc.ul.scrimfinder.util.ErrorResponse;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
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
        description = "Operations for managing players and linking accounts")
public class PlayerController {

    @Inject PlayerService playerService;
    @Inject MatchmakingService matchmakingService;

    @POST
    @Transactional
    @Operation(summary = "Register a new player in the matchmaking platform")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "201",
                        description = "Player registered successfully",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = PlayerDTO.class))),
                @APIResponse(
                        responseCode = "400",
                        description = "Invalid input or registration failed",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "409",
                        description = "Player or Discord Username already exists",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response createPlayer(@Valid CreatePlayerRequest request) {
        PlayerDTO player = playerService.createPlayer(request.id(), request.discordUsername());
        return Response.status(Response.Status.CREATED).entity(player).build();
    }

    @GET
    @Path("/{id}")
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
    public Response getPlayer(@PathParam("id") UUID id) {
        PlayerDTO player = playerService.getPlayer(id);
        return Response.ok(player).build();
    }

    @GET
    @Path("/{id}/tickets")
    @Operation(summary = "Get all tickets for a player")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "200",
                        description = "Player tickets retrieved",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = MatchTicketDTO.class))),
                @APIResponse(
                        responseCode = "404",
                        description = "Player not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response getPlayerTickets(@PathParam("id") UUID id) {
        List<MatchTicketDTO> tickets = matchmakingService.getTicketsByPlayer(id);
        return Response.ok(tickets).build();
    }

    @GET
    @Path("/{id}/lobbies")
    @Operation(summary = "Get all lobbies associated with a player's tickets")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "200",
                        description = "Player lobbies retrieved",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = LobbyDTO.class))),
                @APIResponse(
                        responseCode = "404",
                        description = "Player not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response getPlayerLobbies(@PathParam("id") UUID id) {
        List<LobbyDTO> lobbies = matchmakingService.getLobbiesByPlayer(id);
        return Response.ok(lobbies).build();
    }
}
