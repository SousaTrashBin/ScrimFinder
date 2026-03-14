package fc.ul.scrimfinder.client;

import fc.ul.scrimfinder.dto.response.player.PlayerDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("#league-v4/GET_getLeagueEntriesByPUUID/lol/league/v4/entries")
@RegisterRestClient(configKey = "riot-api")
@RegisterProvider(RiotPlayerServiceExceptionMapper.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface RiotPlayerServiceClient {
    @GET
    @Path("/by-puuid/{encryptedPUUID}")
    PlayerDTO getLeagueEntriesByPUUID(@PathParam("encryptedPUUID") @NotBlank String encryptedPUUID);
}
