package fc.ul.scrimfinder.client;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "riot-api")
@RegisterProvider(ClientUrlPrefixProvider.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface RiotHealthClient {
    @GET
    @Path("/lol/status/v4/platform-data")
    @ClientHeaderParam(name = "X-Riot-Token", value = "${config.riot-api-key}")
    @Retry(maxRetries = 4)
    Response checkHealth();
}
