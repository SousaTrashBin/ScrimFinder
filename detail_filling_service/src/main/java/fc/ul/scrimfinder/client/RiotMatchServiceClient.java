package fc.ul.scrimfinder.client;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "riot-match-api")
@ClientHeaderParam(name = "X-Riot-Token", value = "${config.riot-api-key}")
@RegisterProvider(ClientUrlPrefixProvider.class)
@RegisterProvider(RiotMatchServiceExceptionMapper.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface RiotMatchServiceClient {
    @GET
    @Path("/lol/match/v5/matches/{matchId}")
    @Retry(maxRetries = 4)
    String getMatch(@PathParam("matchId") @NotNull String matchId);
}
