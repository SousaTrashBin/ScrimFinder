package fc.ul.scrimfinder.client;

import io.quarkus.rest.client.reactive.ClientQueryParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "riot-account-api")
@ClientQueryParam(name = "api_key", value = "${config.riot-api-key}")
@RegisterProvider(RiotPlayerServiceExceptionMapper.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface RiotAccountServiceClient {
    @GET
    @Path("/by-riot-id/{gameName}/{tagLine}")
    AccountDTO getByRiotId(
            @PathParam("gameName") @NotBlank String gameName,
            @PathParam("tagLine") @NotBlank String tagLine);
}
