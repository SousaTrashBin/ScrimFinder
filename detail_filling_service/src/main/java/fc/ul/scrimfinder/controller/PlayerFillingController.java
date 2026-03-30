package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.dto.response.player.PlayerDTO;
import fc.ul.scrimfinder.service.PlayerFillingService;
import fc.ul.scrimfinder.util.ErrorResponse;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/players")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(
        name = "Player Filling",
        description = "Operations for retrieving player details from Riot's API")
public class PlayerFillingController {

    @Inject PlayerFillingService playerFillingService;

    @GET
    @Path("/{server}/{name}/{tag}")
    @Operation(summary = "Get complete player information by server, player name and tag")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "200",
                        description = "Successfully retrieved the player details",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = PlayerDTO.class))),
                @APIResponse(
                        responseCode = "400",
                        description = "Invalid server or player ID provided",
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
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "408",
                        description = "Request timeout",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "500",
                        description = "Internal error communication with Riot - unexpected response format",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "503",
                        description = "Riot service unavailable",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "511",
                        description = "The server has an invalid/expired API key with Riot",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    @Timeout(2000)
    public Response getFilledPlayer(
            @PathParam("server") String server,
            @PathParam("name")
                    @Size(
                            min = 3,
                            max = 16,
                            message = "The player name must have between 3 and 16 characters")
                    String name,
            @PathParam("tag")
                    @Size(min = 3, max = 5, message = "The player tag must have between 3 and 5 characters")
                    String tag) {
        PlayerDTO player = playerFillingService.getFilledPlayer(server, name, tag);
        return Response.ok(player).build();
    }
}
