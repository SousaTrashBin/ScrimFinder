package fc.ul.scrimfinder.rest.client;

import fc.ul.scrimfinder.dto.external.ExternalGameDTO;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/v1/riot/matches")
@RegisterRestClient(configKey = "scrimfinder-external-api")
public interface ExternalGameClient {

    @GET
    @Path("/{gameId}")
    ExternalGameDTO fetchMatchResult(@PathParam("gameId") String gameId);
}
