package fc.ul.scrimfinder.client;

import fc.ul.scrimfinder.dto.response.MatchDTO;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "analysis-service")
@RegisterProvider(AnalysisServiceExceptionMapper.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface AnalysisClient {
    // TODO
    @POST
    @Retry(maxRetries = 4)
    MatchDTO createMatch(@BeanParam @Valid MatchDTO match);
}
