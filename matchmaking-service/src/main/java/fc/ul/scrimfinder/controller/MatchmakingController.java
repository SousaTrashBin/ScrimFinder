package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.dto.request.JoinQueueRequest;
import fc.ul.scrimfinder.dto.request.LinkMatchRequest;
import fc.ul.scrimfinder.dto.response.LobbyDTO;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.dto.response.MatchTicketDTO;
import fc.ul.scrimfinder.service.MatchmakingService;
import fc.ul.scrimfinder.util.ErrorResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/tickets")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(
        name = "Matchmaking",
        description = "Operations for joining queues, accepting matches, and reporting results")
public class MatchmakingController {

    @Inject MatchmakingService matchmakingService;

    @POST
    @Operation(
            operationId = "joinQueue",
            summary = "Join a matchmaking queue",
            description = "Requires a linked League of Legends Account via Ranking Service.")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "202",
                        description = "Ticket created and joined queue",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = MatchTicketDTO.class))),
                @APIResponse(
                        responseCode = "400",
                        description = "League of Legends Account not linked",
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
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response joinQueue(@Valid JoinQueueRequest request) {
        MatchTicketDTO ticket = matchmakingService.joinQueue(request);
        return Response.status(Response.Status.ACCEPTED).entity(ticket).build();
    }

    @DELETE
    @Path("/{ticketId}")
    @Operation(operationId = "leaveQueue", summary = "Leave a matchmaking queue")
    @APIResponses(
            value = {
                @APIResponse(responseCode = "204", description = "Successfully left the queue"),
                @APIResponse(
                        responseCode = "404",
                        description = "Ticket not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response leaveQueue(
            @Parameter(
                            description = "Match ticket UUID",
                            example = "550e8400-e29b-41d4-a716-446655440000")
                    @PathParam("ticketId")
                    UUID ticketId) {
        matchmakingService.leaveQueue(ticketId);
        return Response.noContent().build();
    }

    @GET
    @Path("/{ticketId}/lobby")
    @Operation(
            operationId = "getLobbyByTicket",
            summary = "Get the lobby for a specific ticket if matched")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "200",
                        description = "Successfully retrieved lobby info",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = LobbyDTO.class))),
                @APIResponse(
                        responseCode = "404",
                        description = "Ticket not found or match not yet formed",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response getLobby(
            @Parameter(
                            description = "Match ticket UUID",
                            example = "550e8400-e29b-41d4-a716-446655440000")
                    @PathParam("ticketId")
                    UUID ticketId) {
        LobbyDTO lobby = matchmakingService.getLobbyByTicket(ticketId);
        if (lobby == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(lobby).build();
    }

    @POST
    @Path("/matches/{matchId}/players/{playerId}/accept")
    @Operation(operationId = "acceptMatch", summary = "Accept a match proposal")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "200",
                        description = "Match accepted",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = MatchDTO.class))),
                @APIResponse(
                        responseCode = "404",
                        description = "Match not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response acceptMatch(
            @Parameter(description = "Match UUID", example = "550e8400-e29b-41d4-a716-446655440001")
                    @PathParam("matchId")
                    UUID matchId,
            @Parameter(description = "Player UUID", example = "550e8400-e29b-41d4-a716-446655440002")
                    @PathParam("playerId")
                    UUID playerId) {
        MatchDTO match = matchmakingService.acceptMatch(matchId, playerId);
        return Response.ok(match).build();
    }

    @POST
    @Path("/matches/{matchId}/players/{playerId}/decline")
    @Operation(
            operationId = "declineMatch",
            summary = "Decline a match proposal",
            description = "If declined, the match is cancelled and the decliner's ticket is removed.")
    @APIResponses(
            value = {
                @APIResponse(responseCode = "204", description = "Match declined and cancelled"),
                @APIResponse(
                        responseCode = "404",
                        description = "Match not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response declineMatch(
            @Parameter(description = "Match UUID", example = "550e8400-e29b-41d4-a716-446655440001")
                    @PathParam("matchId")
                    UUID matchId,
            @Parameter(description = "Player UUID", example = "550e8400-e29b-41d4-a716-446655440002")
                    @PathParam("playerId")
                    UUID playerId) {
        matchmakingService.declineMatch(matchId, playerId);
        return Response.noContent().build();
    }

    @PUT
    @Path("/matches/{matchId}/link")
    @Operation(
            operationId = "linkMatch",
            summary = "Link an external League of Legends Game ID to the match",
            description =
                    "The externalGameId should follow Riot format REGION_MATCHID (example: VN_1417849076). "
                            + "You can obtain it from a LeagueOfGraphs match URL such as "
                            + "https://www.leagueofgraphs.com/match/vn/1417849076#participant8.")
    @APIResponses(
            value = {
                @APIResponse(responseCode = "200", description = "Match linked successfully"),
                @APIResponse(
                        responseCode = "404",
                        description = "Match not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response linkMatch(
            @Parameter(description = "Match UUID", example = "550e8400-e29b-41d4-a716-446655440001")
                    @PathParam("matchId")
                    UUID matchId,
            @Valid @NotNull LinkMatchRequest request) {
        matchmakingService.linkMatch(matchId, request.externalGameId());
        return Response.ok().build();
    }

    @POST
    @Path("/matches/{matchId}/complete")
    @Operation(
            operationId = "completeMatch",
            summary = "Calculate deltas and report match results to Ranking Service")
    @APIResponses(
            value = {
                @APIResponse(responseCode = "200", description = "Match completed and results reported"),
                @APIResponse(
                        responseCode = "404",
                        description = "Match not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @APIResponse(
                        responseCode = "500",
                        description = "Internal error or remote service communication failure",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response completeMatch(
            @Parameter(description = "Match UUID", example = "550e8400-e29b-41d4-a716-446655440001")
                    @PathParam("matchId")
                    UUID matchId) {
        matchmakingService.completeMatch(matchId);
        return Response.ok().build();
    }
}
