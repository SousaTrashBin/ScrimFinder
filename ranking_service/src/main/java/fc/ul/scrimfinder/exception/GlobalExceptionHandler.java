package fc.ul.scrimfinder.exception;

import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class GlobalExceptionHandler {

    @ServerExceptionMapper
    public RestResponse<String> mapException(PlayerNotFoundException x) {
        return RestResponse.status(Response.Status.NOT_FOUND, x.getMessage());
    }

    @ServerExceptionMapper
    public RestResponse<String> mapException(QueueNotFoundException x) {
        return RestResponse.status(Response.Status.NOT_FOUND, x.getMessage());
    }

    @ServerExceptionMapper
    public RestResponse<String> mapException(MMRAlreadyExistsException x) {
        return RestResponse.status(Response.Status.CONFLICT, x.getMessage());
    }
}