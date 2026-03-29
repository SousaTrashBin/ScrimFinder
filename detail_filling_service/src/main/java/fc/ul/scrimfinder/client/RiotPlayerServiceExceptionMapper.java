package fc.ul.scrimfinder.client;

import fc.ul.scrimfinder.exception.ExternalServiceUnavailableException;
import fc.ul.scrimfinder.exception.PlayerNotFoundException;
import fc.ul.scrimfinder.exception.UnauthorizedException;
import fc.ul.scrimfinder.util.ColoredMessage;
import fc.ul.scrimfinder.util.ErrorResponse;
import fc.ul.scrimfinder.util.LogColor;
import fc.ul.scrimfinder.util.RiotErrorMessageConverter;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;
import org.jboss.logging.Logger;

public class RiotPlayerServiceExceptionMapper implements ResponseExceptionMapper<RuntimeException> {

    @Inject Logger logger;

    @Override
    public RuntimeException toThrowable(Response response) {
        try {
            ErrorResponse errorResponse = RiotErrorMessageConverter.convertRiotErrorMessage(response);
            int code = Integer.parseInt(errorResponse.getCode());
            String message = errorResponse.getMessage();

            if (code == 401 || code == 503) {
                logger.error(ColoredMessage.withColor(message, LogColor.RED));
            } else {
                logger.warn(ColoredMessage.withColor(message, LogColor.YELLOW));
            }

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
