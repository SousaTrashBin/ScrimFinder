package fc.ul.scrimfinder.exception;

import fc.ul.scrimfinder.util.ErrorResponse;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

@Slf4j
public class GlobalExceptionHandler {

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
    public RestResponse<ErrorResponse> mapException(MMRAlreadyExistsException x) {
        log.warn("MMRAlreadyExistsException: {}", x.getMessage());
        return RestResponse.status(
                Response.Status.CONFLICT,
                ErrorResponse.builder().code("MMR_ALREADY_EXISTS").message(x.getMessage()).build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(PlayerAlreadyCreatedException x) {
        log.warn("PlayerAlreadyCreatedException: {}", x.getMessage());
        return RestResponse.status(
                Response.Status.CONFLICT,
                ErrorResponse.builder().code("PLAYER_ALREADY_EXISTS").message(x.getMessage()).build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(ExternalAccountNotFoundException x) {
        log.warn("ExternalAccountNotFoundException: {}", x.getMessage());
        return RestResponse.status(
                Response.Status.BAD_REQUEST,
                ErrorResponse.builder().code("EXTERNAL_ACCOUNT_NOT_FOUND").message(x.getMessage()).build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(LeagueAccountNotLinkedException x) {
        log.warn("LeagueAccountNotLinkedException: {}", x.getMessage());
        return RestResponse.status(
                Response.Status.BAD_REQUEST,
                ErrorResponse.builder().code("LEAGUE_ACCOUNT_NOT_LINKED").message(x.getMessage()).build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(ExternalServiceUnavailableException x) {
        log.error("ExternalServiceUnavailableException: {}", x.getMessage());
        return RestResponse.status(
                Response.Status.SERVICE_UNAVAILABLE,
                ErrorResponse.builder()
                        .code("EXTERNAL_SERVICE_UNAVAILABLE")
                        .message(x.getMessage())
                        .build());
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
