package fc.ul.scrimfinder.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;

public class RiotErrorMessageConverter {
    public static ErrorResponse convertRiotErrorMessage(Response response)
            throws JsonProcessingException {
        String errorJson = response.readEntity(String.class);
        ObjectMapper mapper = new ObjectMapper();
        RiotErrorResponseWithStatus errorResponseWithStatus = new RiotErrorResponseWithStatus();
        RiotErrorResponseWithoutStatus errorResponseWithoutStatus =
                new RiotErrorResponseWithoutStatus();
        Integer code = null;
        String message = "";

        try {
            errorResponseWithStatus = mapper.readValue(errorJson, RiotErrorResponseWithStatus.class);
            code = errorResponseWithStatus.getStatus().getStatus_code();
            message = errorResponseWithStatus.getStatus().getMessage();
            return new ErrorResponse(String.valueOf(code), message);
        } catch (Exception x) {
            errorResponseWithoutStatus =
                    mapper.readValue(errorJson, RiotErrorResponseWithoutStatus.class);
            code = errorResponseWithoutStatus.getHttpStatus();
            message = errorResponseWithoutStatus.getImplementationDetails();
            return new ErrorResponse(String.valueOf(code), message);
        }
    }
}
