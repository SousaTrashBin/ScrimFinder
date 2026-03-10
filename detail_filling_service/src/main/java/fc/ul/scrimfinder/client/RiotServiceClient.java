package fc.ul.scrimfinder.client;

import fc.ul.scrimfinder.dto.response.match.MatchDto;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/lol/match/v5")
@RegisterRestClient(configKey = "riot-api")
@RegisterProvider(RiotServiceExceptionMapper.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface RiotServiceClient {
    @GET
    @Path("/matches/{matchId}")
    MatchDto getMatch(@PathParam("matchId") @NotNull String matchId);
}
