package fc.ul.scrimfinder.client;

import io.quarkus.rest.client.reactive.ClientQueryParam;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "riot-match-api")
@ClientQueryParam(name = "api_key", value = "${config.riot-api-key}")
@RegisterProvider(ClientUrlPrefixProvider.class)
@RegisterProvider(RiotMatchServiceExceptionMapper.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface RiotMatchServiceClient {
    @GET
    @Path("/{matchId}")
    @Retry(maxRetries = 4)
    String getMatch(@PathParam("matchId") @NotNull String matchId);
}
