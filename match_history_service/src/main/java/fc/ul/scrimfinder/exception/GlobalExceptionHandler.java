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
                ErrorResponse.builder()
                        .code("MATCH_NOT_FOUND")
                        .message(x.getMessage())
                        .build()
        );
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(ChampionNotFoundException x) {
        return RestResponse.status(
                Response.Status.NOT_FOUND,
                ErrorResponse.builder()
                        .code("CHAMPION_NOT_FOUND")
                        .message(x.getMessage())
                        .build()
        );
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(RankNotFoundException x) {
        return RestResponse.status(
                Response.Status.NOT_FOUND,
                ErrorResponse.builder()
                        .code("RANK_NOT_FOUND")
                        .message(x.getMessage())
                        .build()
        );
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(InvalidIntervalException x) {
        return RestResponse.status(
                Response.Status.NOT_FOUND,
                ErrorResponse.builder()
                        .code("INTERVAL_INVALID")
                        .message(x.getMessage())
                        .build()
        );
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(InvalidTeamsException x) {
        return RestResponse.status(
                Response.Status.NOT_FOUND,
                ErrorResponse.builder()
                        .code("TEAMS_INVALID")
                        .message(x.getMessage())
                        .build()
        );
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(InvalidRoleException x) {
        return RestResponse.status(
                Response.Status.NOT_FOUND,
                ErrorResponse.builder()
                        .code("ROLE_INVALID")
                        .message(x.getMessage())
                        .build()
        );
    }

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
    public RestResponse<ErrorResponse> mapException(MatchAlreadyExistsException x) {
        return RestResponse.status(
                Response.Status.NOT_FOUND,
                ErrorResponse.builder()
                        .code("MATCH_ALREADY_EXISTS")
                        .message(x.getMessage())
                        .build()
        );
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(ExternalServiceUnavailableException x) {
        return RestResponse.status(
                Response.Status.NOT_FOUND,
                ErrorResponse.builder()
                        .code("EXTERNAL_SERVICE_UNAVAILABLE")
                        .message(x.getMessage())
                        .build()
        );
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapException(WebApplicationException x) {
        return RestResponse.status(
                Response.Status.fromStatusCode(x.getResponse().getStatus()),
                ErrorResponse.builder()
                        .code("WEB_APPLICATION_ERROR")
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
