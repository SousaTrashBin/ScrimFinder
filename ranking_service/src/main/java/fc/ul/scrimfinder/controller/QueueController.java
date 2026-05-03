package fc.ul.scrimfinder.controller;

import fc.ul.scrimfinder.dto.request.UpdateQueueRequest;
import fc.ul.scrimfinder.dto.response.QueueDTO;
import fc.ul.scrimfinder.service.QueueService;
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
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/queue")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Queue Management", description = "Operations for managing MMR-based queues and rules")
public class QueueController {

    @Inject QueueService queueService;

    @GET
    @Path("/{queueId}")
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
    public Response getQueue(@PathParam("queueId") UUID queueId) {
        var queue = queueService.getQueue(queueId);
        return Response.ok(queue).build();
    }

    @PUT
    @Path("/{queueId}")
    @Operation(summary = "Update an existing queue's configuration")
    @APIResponses(
            value = {
                @APIResponse(
                        responseCode = "200",
                        description = "Queue configuration successfully updated",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = QueueDTO.class))),
                @APIResponse(
                        responseCode = "400",
                        description = "Invalid update payload",
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
    public Response updateQueue(
            @PathParam("queueId") UUID queueId, @NotNull @Valid UpdateQueueRequest updateDTO) {
        var updatedQueue = queueService.updateQueue(queueId, updateDTO);
        return Response.ok(updatedQueue).build();
    }

    @DELETE
    @Path("/{queueId}")
    @Operation(summary = "Delete a specific queue")
    @APIResponses(
            value = {
                @APIResponse(responseCode = "204", description = "Queue successfully deleted"),
                @APIResponse(
                        responseCode = "404",
                        description = "Queue not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Response deleteQueue(@PathParam("queueId") UUID queueId) {
        queueService.deleteQueue(queueId);
        return Response.noContent().build();
    }
}
