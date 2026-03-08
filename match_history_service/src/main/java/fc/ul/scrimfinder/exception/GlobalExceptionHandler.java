package fc.ul.scrimfinder.exception;

import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class GlobalExceptionHandler {

    @ServerExceptionMapper
    public RestResponse<String> mapException(MatchNotFoundException e) {
        return RestResponse.status(Response.Status.NOT_FOUND, e.getMessage());
    }

    // TODO
}
