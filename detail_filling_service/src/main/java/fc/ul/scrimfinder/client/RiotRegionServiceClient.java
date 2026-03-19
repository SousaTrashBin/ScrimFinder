package fc.ul.scrimfinder.client;

import io.quarkus.rest.client.reactive.ClientQueryParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "riot-region-api")
@ClientQueryParam(name = "api_key", value = "${config.riot-api-key}")
@RegisterProvider(RiotPlayerServiceExceptionMapper.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface RiotRegionServiceClient {
    @GET
    @Path("/{puuid}")
    String getActiveRegion(@PathParam("puuid") @NotBlank String puuid);
}
