package fc.ul.scrimfinder.exception;

import fc.ul.scrimfinder.util.ErrorResponse;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class GlobalExceptionHandler {

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(PlayerNotFoundException x) {
        return RestResponse.status(
                Response.Status.NOT_FOUND,
                ErrorResponse.builder().code("PLAYER_NOT_FOUND").message(x.getMessage()).build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(QueueNotFoundException x) {
        return RestResponse.status(
                Response.Status.NOT_FOUND,
                ErrorResponse.builder().code("QUEUE_NOT_FOUND").message(x.getMessage()).build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(MMRAlreadyExistsException x) {
        return RestResponse.status(
                Response.Status.CONFLICT,
                ErrorResponse.builder().code("MMR_ALREADY_EXISTS").message(x.getMessage()).build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(PlayerAlreadyCreatedException x) {
        return RestResponse.status(
                Response.Status.CONFLICT,
                ErrorResponse.builder().code("PLAYER_ALREADY_EXISTS").message(x.getMessage()).build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(ExternalAccountNotFoundException x) {
        return RestResponse.status(
                Response.Status.BAD_REQUEST,
                ErrorResponse.builder().code("EXTERNAL_ACCOUNT_NOT_FOUND").message(x.getMessage()).build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(LeagueAccountNotLinkedException x) {
        return RestResponse.status(
                Response.Status.BAD_REQUEST,
                ErrorResponse.builder().code("LEAGUE_ACCOUNT_NOT_LINKED").message(x.getMessage()).build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(ExternalServiceUnavailableException x) {
        return RestResponse.status(
                Response.Status.SERVICE_UNAVAILABLE,
                ErrorResponse.builder()
                        .code("EXTERNAL_SERVICE_UNAVAILABLE")
                        .message(x.getMessage())
                        .build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(Exception x) {
        return RestResponse.status(
                Response.Status.INTERNAL_SERVER_ERROR,
                ErrorResponse.builder()
                        .code("INTERNAL_SERVER_ERROR")
                        .message("An unexpected error occurred. Please try again later.")
                        .build());
    }
}
