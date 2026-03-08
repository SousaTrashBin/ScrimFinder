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
                ErrorResponse.builder()
                        .code("PLAYER_NOT_FOUND")
                        .message(x.getMessage())
                        .build()
        );
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(QueueNotFoundException x) {
        return RestResponse.status(
                Response.Status.NOT_FOUND,
                ErrorResponse.builder()
                        .code("QUEUE_NOT_FOUND")
                        .message(x.getMessage())
                        .build()
        );
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(TicketNotFoundException x) {
        return RestResponse.status(
                Response.Status.NOT_FOUND,
                ErrorResponse.builder()
                        .code("TICKET_NOT_FOUND")
                        .message(x.getMessage())
                        .build()
        );
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(LeagueAccountNotLinkedException x) {
        return RestResponse.status(
                Response.Status.BAD_REQUEST,
                ErrorResponse.builder()
                        .code("LEAGUE_ACCOUNT_NOT_LINKED")
                        .message(x.getMessage())
                        .build()
        );
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(WebApplicationException x) {
        return RestResponse.status(
                Response.Status.fromStatusCode(x.getResponse().getStatus()),
                ErrorResponse.builder()
                        .code("REMOTE_SERVICE_ERROR")
                        .message(x.getMessage())
                        .build()
        );
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(Exception x) {
        return RestResponse.status(
                Response.Status.INTERNAL_SERVER_ERROR,
                ErrorResponse.builder()
                        .code("INTERNAL_SERVER_ERROR")
                        .message("An unexpected error occurred. Please try again later.")
                        .build()
        );
    }
}
