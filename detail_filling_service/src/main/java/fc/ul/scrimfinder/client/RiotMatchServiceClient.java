package fc.ul.scrimfinder.client;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "riot-match-api")
@RegisterProvider(RiotMatchServiceExceptionMapper.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface RiotMatchServiceClient {
    @GET
    @Path("/{matchId}")
    String getMatch(@PathParam("matchId") @NotNull String matchId, @QueryParam("api_key") @NotNull String apiKey);
}
