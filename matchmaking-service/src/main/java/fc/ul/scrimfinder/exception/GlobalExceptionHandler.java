package fc.ul.scrimfinder.exception;

import fc.ul.scrimfinder.util.ErrorResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

@Slf4j
public class GlobalExceptionHandler {

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(PlayerAlreadyExistsException x) {
        log.warn("PlayerAlreadyExistsException: {}", x.getMessage());
        return RestResponse.status(
                Response.Status.CONFLICT,
                ErrorResponse.builder().code("PLAYER_ALREADY_EXISTS").message(x.getMessage()).build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(PlayerNotFoundException x) {
        log.warn("PlayerNotFoundException: {}", x.getMessage());
        return RestResponse.status(
                Response.Status.NOT_FOUND,
                ErrorResponse.builder().code("PLAYER_NOT_FOUND").message(x.getMessage()).build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(QueueNotFoundException x) {
        log.warn("QueueNotFoundException: {}", x.getMessage());
        return RestResponse.status(
                Response.Status.NOT_FOUND,
                ErrorResponse.builder().code("QUEUE_NOT_FOUND").message(x.getMessage()).build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(TicketNotFoundException x) {
        log.warn("TicketNotFoundException: {}", x.getMessage());
        return RestResponse.status(
                Response.Status.NOT_FOUND,
                ErrorResponse.builder().code("TICKET_NOT_FOUND").message(x.getMessage()).build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(LeagueAccountNotLinkedException x) {
        log.warn("LeagueAccountNotLinkedException: {}", x.getMessage());
        return RestResponse.status(
                Response.Status.BAD_REQUEST,
                ErrorResponse.builder().code("LEAGUE_ACCOUNT_NOT_LINKED").message(x.getMessage()).build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(IllegalArgumentException x) {
        log.warn("IllegalArgumentException: {}", x.getMessage());
        return RestResponse.status(
                Response.Status.BAD_REQUEST,
                ErrorResponse.builder().code("BAD_REQUEST").message(x.getMessage()).build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(WebApplicationException x) {
        int status = x.getResponse().getStatus();
        log.error("WebApplicationException: {} - Status: {}", x.getMessage(), status);

        String code = status >= 500 ? "REMOTE_SERVICE_ERROR" : "BAD_REQUEST";
        if (status == 404) code = "NOT_FOUND";

        return RestResponse.status(
                Response.Status.fromStatusCode(status),
                ErrorResponse.builder().code(code).message(x.getMessage()).build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(Exception x) {
        log.error("Unhandled Exception: {}", x.getMessage(), x);
        return RestResponse.status(
                Response.Status.INTERNAL_SERVER_ERROR,
                ErrorResponse.builder()
                        .code("INTERNAL_SERVER_ERROR")
                        .message("An unexpected error occurred. Please try again later.")
                        .build());
    }
}
