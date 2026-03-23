package fc.ul.scrimfinder.exception;

import fc.ul.scrimfinder.util.ErrorResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class GlobalExceptionHandler {

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(MatchNotFoundException x) {
        return RestResponse.status(
                Response.Status.NOT_FOUND,
                ErrorResponse.builder().code("MATCH_NOT_FOUND").message(x.getMessage()).build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(PlayerNotFoundException x) {
        return RestResponse.status(
                Response.Status.NOT_FOUND,
                ErrorResponse.builder().code("PLAYER_NOT_FOUND").message(x.getMessage()).build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(InvalidMatchFormatException x) {
        return RestResponse.status(
                Response.Status.INTERNAL_SERVER_ERROR,
                ErrorResponse.builder().code("INVALID_MATCH_FORMAT").message(x.getMessage()).build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(InvalidPlayerFormatException x) {
        return RestResponse.status(
                Response.Status.INTERNAL_SERVER_ERROR,
                ErrorResponse.builder().code("INVALID_PLAYER_FORMAT").message(x.getMessage()).build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(InvalidTeamFormatException x) {
        return RestResponse.status(
                Response.Status.INTERNAL_SERVER_ERROR,
                ErrorResponse.builder().code("INVALID_TEAM_FORMAT").message(x.getMessage()).build());
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(UnauthorizedException x) {
        return RestResponse.status(
                Response.Status.UNAUTHORIZED,
                ErrorResponse.builder().code("UNAUTHORIZED_ACCESS").message(x.getMessage()).build());
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
    public RestResponse<ErrorResponse> mapException(WebApplicationException x) {
        return RestResponse.status(
                Response.Status.fromStatusCode(x.getResponse().getStatus()),
                ErrorResponse.builder().code("WEB_APPLICATION_ERROR").message(x.getMessage()).build());
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
