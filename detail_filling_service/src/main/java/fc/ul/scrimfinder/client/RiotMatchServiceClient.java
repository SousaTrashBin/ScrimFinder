package fc.ul.scrimfinder.client;

import fc.ul.scrimfinder.dto.response.match.MatchDto;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("#match-v5/GET_getMatch/lol/match/v5/matches")
@RegisterRestClient(configKey = "riot-api")
@RegisterProvider(RiotMatchServiceExceptionMapper.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface RiotMatchServiceClient {
    @GET
    @Path("/{matchId}")
    MatchDto getMatch(@PathParam("matchId") @NotNull String matchId);
}
