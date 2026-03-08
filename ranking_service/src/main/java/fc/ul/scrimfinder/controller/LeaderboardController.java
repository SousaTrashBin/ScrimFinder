package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.service.PlayerRankingService;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Optional;

@Path("/leaderboards")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Leaderboard", description = "Global and regional leaderboard operations")
public class LeaderboardController {

    @Inject
    PlayerRankingService playerRankingService;

    @GET
    @Operation(summary = "Get queue leaderboard with pagination")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "Successfully retrieved the leaderboard"),
            @APIResponse(responseCode = "400", description = "Invalid pagination parameters (e.g., size < 0 or > 100)"),
            @APIResponse(responseCode = "404", description = "Queue not found (if queueId is provided but invalid)")
    })
    public Response getQueueLeaderboard(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") @Min(0) @Max(100) int size,
            @QueryParam("queueId") Optional<Long> queueId
    ) {
        var leaderboard = playerRankingService.getQueueLeaderboard(page, size, queueId);
        return Response.ok(leaderboard).build();
    }
}