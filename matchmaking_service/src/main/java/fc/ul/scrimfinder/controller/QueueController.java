package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.client.RankingServiceClient;
import fc.ul.scrimfinder.dto.response.QueueDTO;
import fc.ul.scrimfinder.service.QueueService;
import fc.ul.scrimfinder.util.ErrorResponse;
import fc.ul.scrimfinder.util.MatchmakingMode;
import fc.ul.scrimfinder.util.Region;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Path("/queues")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(
        name = "Queue Management",
        description = "Operations for managing matchmaking queues and rules")
public class QueueController {

    @Inject QueueService queueService;

    @Inject @RestClient RankingServiceClient rankingServiceClient;

    @POST
    @Path("/{id}")
    @Operation(summary = "Create a new queue (can be global or namespaced/local)")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "201",
                        description = "Queue created successfully",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = QueueDTO.class))),
                @APIResponse(
                        responseCode = "409",
                        description = "Queue ID already exists",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response createQueue(
            @PathParam("id") Long id,
            @QueryParam("name") String name,
            @QueryParam("namespace") String namespace,
            @QueryParam("requiredPlayers") @DefaultValue("10") int requiredPlayers,
            @QueryParam("isRoleQueue") @DefaultValue("false") boolean isRoleQueue,
            @QueryParam("mode") @DefaultValue("NORMAL") MatchmakingMode mode,
            @QueryParam("mmrWindow") @DefaultValue("200") int mmrWindow,
            @QueryParam("region") Region region) {
        QueueDTO queue =
                queueService.createQueue(
                        id, name, namespace, requiredPlayers, isRoleQueue, mode, mmrWindow, region);
        return Response.status(Response.Status.CREATED).entity(queue).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get queue details")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "200",
                        description = "Queue found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = QueueDTO.class))),
                @APIResponse(
                        responseCode = "404",
                        description = "Queue not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response getQueue(@PathParam("id") Long id) {
        QueueDTO queue = queueService.getQueue(id);
        return Response.ok(queue).build();
    }
}
