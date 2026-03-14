package fc.ul.scrimfinder.client;

import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;
import fc.ul.scrimfinder.exception.PlayerNotFoundException;
import fc.ul.scrimfinder.util.ErrorResponse;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

public class RiotPlayerServiceExceptionMapper implements ResponseExceptionMapper<RuntimeException> {
    @Override
    public RuntimeException toThrowable(Response response) {
        try {
            ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
            String code = errorResponse.getCode();
            String message = errorResponse.getMessage();

            return switch (code) {
                case "404" -> new PlayerNotFoundException(message);
                case "503" -> new ExternalServiceUnavailableException(message);
                default -> new RuntimeException("Remote service error: " + message);
            };
        } catch (Exception e) {
            return new RuntimeException("Remote service error: " + response.getStatus());
        }
    }

    @Override
    public boolean handles(int status, MultivaluedMap<String, Object> headers) {
        return status >= 400;
    }
}