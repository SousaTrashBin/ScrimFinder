package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.dto.response.PlayerDTO;
import fc.ul.scrimfinder.service.PlayerService;
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
@Tag(
        name = "Player Management",
        description = "Operations for managing players and linking accounts")
public class PlayerController {

    @Inject PlayerService playerService;

    @POST
    @Operation(
            summary = "Create a new player and register in Ranking Service",
            description = "Atomic operation: rolls back local creation if remote registration fails.")
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
                        description = "Player or Username already exists",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "500",
                        description = "Remote service registration failed",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response createPlayer(
            @QueryParam("id") UUID id, @QueryParam("username") String username) {
        PlayerDTO player = playerService.createPlayer(id, username);
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
}
