package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.dto.request.CreateQueueRequest;
import fc.ul.scrimfinder.dto.response.QueueDTO;
import fc.ul.scrimfinder.service.QueueService;
import fc.ul.scrimfinder.util.ErrorResponse;
import fc.ul.scrimfinder.util.MatchmakingMode;
import fc.ul.scrimfinder.util.Region;
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
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/queues")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(
        name = "Queue Management",
        description = "Operations for managing matchmaking queues and rules")
public class QueueController {

    @Inject QueueService queueService;

    @POST
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
    public Response createQueue(@Valid @NotNull CreateQueueRequest request) {
        int requiredPlayers = request.requiredPlayers() > 0 ? request.requiredPlayers() : 10;
        MatchmakingMode mode = request.mode() == null ? MatchmakingMode.NORMAL : request.mode();
        int mmrWindow = request.mmrWindow() > 0 ? request.mmrWindow() : 200;
        Region region = request.region();
        QueueDTO queue =
                queueService.createQueue(
                        request.id(),
                        request.name(),
                        request.namespace(),
                        requiredPlayers,
                        request.isRoleQueue(),
                        mode,
                        mmrWindow,
                        region);
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
    public Response getQueue(@PathParam("id") UUID id) {
        QueueDTO queue = queueService.getQueue(id);
        return Response.ok(queue).build();
    }
}
