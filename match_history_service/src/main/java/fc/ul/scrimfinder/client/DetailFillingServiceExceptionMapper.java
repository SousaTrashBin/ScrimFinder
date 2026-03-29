package fc.ul.scrimfinder.client;

import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;
import fc.ul.scrimfinder.exception.MatchNotFoundException;
import fc.ul.scrimfinder.util.ColoredMessage;
import fc.ul.scrimfinder.util.ErrorResponse;
import fc.ul.scrimfinder.util.LogColor;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;
import org.jboss.logging.Logger;

public class DetailFillingServiceExceptionMapper
        implements ResponseExceptionMapper<RuntimeException> {
    @Inject Logger logger;

    @Override
    public RuntimeException toThrowable(Response response) {
        try {
            ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
            String code = errorResponse.getCode();
            String message = errorResponse.getMessage();

            if (code.equalsIgnoreCase("UNAUTHORIZED_ACCESS")
                    || code.equalsIgnoreCase("EXTERNAL_SERVICE_UNAVAILABLE")) {
                logger.error(ColoredMessage.withColor(message, LogColor.RED));
            } else {
                logger.warn(ColoredMessage.withColor(message, LogColor.YELLOW));
            }

            return switch (code) {
                case "MATCH_NOT_FOUND" -> new MatchNotFoundException(message);
                case "UNAUTHORIZED_ACCESS", "EXTERNAL_SERVICE_UNAVAILABLE" ->
                        new ExternalServiceUnavailableException(message);
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
