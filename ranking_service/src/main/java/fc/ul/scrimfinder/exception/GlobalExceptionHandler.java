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
    public RestResponse<ErrorResponse> mapException(MMRAlreadyExistsException x) {
        return RestResponse.status(
                Response.Status.CONFLICT,
                new ErrorResponse("MMR_ALREADY_EXISTS", x.getMessage())
        );
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(PlayerAlreadyCreatedException x) {
        return RestResponse.status(
                Response.Status.CONFLICT,
                new ErrorResponse("PLAYER_ALREADY_EXISTS", x.getMessage())
        );
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(ExternalAccountNotFoundException x) {
        return RestResponse.status(
                Response.Status.BAD_REQUEST,
                new ErrorResponse("EXTERNAL_ACCOUNT_NOT_FOUND", x.getMessage())
        );
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(LeagueAccountNotLinkedException x) {
        return RestResponse.status(
                Response.Status.BAD_REQUEST,
                new ErrorResponse("LEAGUE_ACCOUNT_NOT_LINKED", x.getMessage())
        );
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(ExternalServiceUnavailableException x) {
        return RestResponse.status(
                Response.Status.SERVICE_UNAVAILABLE,
                new ErrorResponse("EXTERNAL_SERVICE_UNAVAILABLE", x.getMessage())
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