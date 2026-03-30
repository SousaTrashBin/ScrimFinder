package fc.ul.scrimfinder.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "riot-region-api")
@ClientHeaderParam(name = "X-Riot-Token", value = "${config.riot-api-key}")
@RegisterProvider(ClientUrlPrefixProvider.class)
@RegisterProvider(RiotPlayerServiceExceptionMapper.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface RiotRegionServiceClient {
    @GET
    @Path("/{puuid}")
    @Retry(maxRetries = 4)
    String getActiveRegion(@PathParam("puuid") @NotBlank String puuid);
}
