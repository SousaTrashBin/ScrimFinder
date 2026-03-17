package fc.ul.scrimfinder.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;
import fc.ul.scrimfinder.exception.MatchNotFoundException;
import fc.ul.scrimfinder.util.RiotMatchErrorResponse;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

public class RiotMatchServiceExceptionMapper implements ResponseExceptionMapper<RuntimeException> {

    @Override
    public RuntimeException toThrowable(Response response) {
        try {
            String errorJson = response.readEntity(String.class);
            ObjectMapper mapper = new ObjectMapper();
            RiotMatchErrorResponse errorResponse = mapper.readValue(errorJson, RiotMatchErrorResponse.class);
            Integer code = errorResponse.getHttpStatus();
            String message = errorResponse.getImplementationDetails();

            return switch (code) {
                case 404 -> new MatchNotFoundException(message);
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
