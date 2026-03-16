package fc.ul.scrimfinder.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("#league-v4/GET_getLeagueEntriesByPUUID/lol/league/v4/entries")
@RegisterRestClient(configKey = "riot-match-api")
@RegisterProvider(RiotPlayerServiceExceptionMapper.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface RiotPlayerServiceClient {
    @GET
    @Path("/by-puuid/{encryptedPUUID}")
    String getLeagueEntriesByPUUID(@PathParam("encryptedPUUID") @NotBlank String encryptedPUUID);
}
