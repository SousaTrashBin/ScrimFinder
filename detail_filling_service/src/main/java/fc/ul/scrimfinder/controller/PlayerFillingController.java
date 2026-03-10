package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.dto.response.player.PlayerDto;
import fc.ul.scrimfinder.service.PlayerFillingService;
import fc.ul.scrimfinder.util.ErrorResponse;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Pattern;
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
@Tag(name = "Player Filling", description = "Operations for retrieving player details from Riot's API")
public class PlayerFillingController {

    @Inject
    PlayerFillingService playerFillingService;

    @GET
    @Path("/{playerId}")
    @Operation(summary = "Get complete player information by player name and tag")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Successfully retrieved the player details",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlayerDto.class))),
            @APIResponse(responseCode = "400", description = "Invalid player ID provided",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @APIResponse(responseCode = "404", description = "Player not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Response getPlayerById(@PathParam("playerId") @Pattern(regexp = "^.+#.+$") String playerId) {
        PlayerDto player = playerFillingService.getPlayerById(playerId);
        return Response.ok(player).build();
    }
}

