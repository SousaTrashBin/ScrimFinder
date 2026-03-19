package fc.ul.scrimfinder.client;

import fc.ul.scrimfinder.dto.response.player.AccountDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("#account-v1/GET_getByRiotId/riot/account/v1/accounts")
@RegisterRestClient(configKey = "riot-api")
@RegisterProvider(RiotAccountServiceExceptionMapper.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface RiotAccountServiceClient {
    @GET
    @Path("/by-riot-id/{gameName}/{tagLine}")
    AccountDTO getByRiotId(
            @PathParam("gameName") @NotBlank String gameName,
            @PathParam("tagLine") @NotBlank String tagLine);
}
