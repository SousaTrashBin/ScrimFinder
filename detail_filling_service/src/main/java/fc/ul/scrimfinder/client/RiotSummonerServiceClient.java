package fc.ul.scrimfinder.client;

import io.quarkus.rest.client.reactive.ClientQueryParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "riot-summoner-api")
@ClientQueryParam(name = "api_key", value = "${config.riot-api-key}")
@RegisterProvider(ClientUrlPrefixProvider.class)
@RegisterProvider(RiotPlayerServiceExceptionMapper.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface RiotSummonerServiceClient {
    @GET
    @Path("/{encryptedPUUID}")
    @Retry(maxRetries = 4)
    String getByAccessToken(@PathParam("encryptedPUUID") @NotBlank String encryptedPUUID);
}
