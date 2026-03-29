package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.service.PlayerRankingService;
import fc.ul.scrimfinder.util.ErrorResponse;
import fc.ul.scrimfinder.util.Region;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Optional;
import java.util.UUID;

@Path("/leaderboards")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Leaderboard", description = "Global and queue-specific leaderboard operations")
public class LeaderboardController {

    @Inject PlayerRankingService playerRankingService;

    @GET
    @Operation(summary = "Get queue leaderboard with pagination")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "200",
                        description = "Successfully retrieved the leaderboard",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = PaginatedResponseDTO.class))),
                @APIResponse(
                        responseCode = "400",
                        description = "Invalid pagination parameters",
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
    public Response getQueueLeaderboard(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") @Min(0) @Max(100) int size,
            @QueryParam("queueId") Optional<UUID> queueId,
            @QueryParam("region") Optional<Region> region) {
        var leaderboard = playerRankingService.getQueueLeaderboard(page, size, queueId, region);
        return Response.ok(leaderboard).build();
    }
}
