package fc.ul.scrimfinder.rest.client;

import fc.ul.scrimfinder.dto.external.ExternalPlayerResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/v1/riot/players")
@RegisterRestClient(configKey = "scrimfinder-external-api")
public interface ExternalPlayerClient {

    @GET
    @Path("/{server}/{name}/{tag}")
    ExternalPlayerResponse fetchPlayerRank(
            @PathParam("server") String server,
            @PathParam("name") String name,
            @PathParam("tag") String tag);
}
