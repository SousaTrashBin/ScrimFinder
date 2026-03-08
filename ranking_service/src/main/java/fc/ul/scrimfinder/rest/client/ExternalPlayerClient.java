package fc.ul.scrimfinder.rest.client;

import fc.ul.scrimfinder.dto.external.ExternalPlayerDTO;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/v1/external/players")
@RegisterRestClient(configKey = "scrimfinder-external-player-api")
public interface ExternalPlayerClient {

    @GET
    @Path("/{playerId}")
    ExternalPlayerDTO fetchPlayerRank(@PathParam("playerId") String playerId);
}