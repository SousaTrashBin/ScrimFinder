package fc.ul.scrimfinder.client;

import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;
import fc.ul.scrimfinder.exception.PlayerNotFoundException;
import fc.ul.scrimfinder.exception.UnauthorizedException;
import fc.ul.scrimfinder.util.JsonNodeFinder;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

import java.util.Objects;

public class RiotPlayerServiceExceptionMapper implements ResponseExceptionMapper<RuntimeException> {

    @Override
    public RuntimeException toThrowable(Response response) {
        try {
            JsonNodeFinder statusNode = Objects.requireNonNull(new JsonNodeFinder(null)
                            .fromStringOrThrow(response.readEntity(String.class), RuntimeException.class))
                    .jsonGetOrThrow("status", RuntimeException.class);

            int code = statusNode
                    .jsonGetOrThrow("status_code", RuntimeException.class)
                    .jsonNode().asInt();

            String message = statusNode
                    .jsonGetOrThrow("message", RuntimeException.class)
                    .jsonNode().asText();

            return switch (code) {
                case 401 -> new UnauthorizedException(message);
                case 400, 404 -> new PlayerNotFoundException(message);
                case 503 -> new ExternalServiceUnavailableException(message);
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