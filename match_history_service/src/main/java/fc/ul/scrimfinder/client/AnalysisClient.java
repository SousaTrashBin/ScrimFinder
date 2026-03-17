package fc.ul.scrimfinder.client;

import fc.ul.scrimfinder.dto.response.MatchDTO;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/v1/analysis")
@RegisterRestClient(configKey = "analysis-api")
@RegisterProvider(AnalysisServiceExceptionMapper.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface AnalysisClient {
    // TODO
    @POST
    MatchDTO createMatch(@BeanParam @Valid MatchDTO match);
}
