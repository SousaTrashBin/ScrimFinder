package fc.ul.scrimfinder.exception;

import fc.ul.scrimfinder.util.ErrorResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class GlobalExceptionHandler {

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(PlayerNotFoundException x) {
        return RestResponse.status(
                Response.Status.NOT_FOUND,
                new ErrorResponse("PLAYER_NOT_FOUND", x.getMessage())
        );
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(QueueNotFoundException x) {
        return RestResponse.status(
                Response.Status.NOT_FOUND,
                new ErrorResponse("QUEUE_NOT_FOUND", x.getMessage())
        );
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(TicketNotFoundException x) {
        return RestResponse.status(
                Response.Status.NOT_FOUND,
                new ErrorResponse("TICKET_NOT_FOUND", x.getMessage())
        );
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(RiotAccountNotLinkedException x) {
        return RestResponse.status(
                Response.Status.BAD_REQUEST,
                new ErrorResponse("RIOT_ACCOUNT_NOT_LINKED", x.getMessage())
        );
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(WebApplicationException x) {
        return RestResponse.status(
                Response.Status.fromStatusCode(x.getResponse().getStatus()),
                new ErrorResponse("REMOTE_SERVICE_ERROR", x.getMessage())
        );
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(Exception x) {
        return RestResponse.status(
                Response.Status.INTERNAL_SERVER_ERROR,
                new ErrorResponse(
                        "INTERNAL_SERVER_ERROR",
                        "An unexpected error occurred. Please try again later."
                )
        );
    }
}
