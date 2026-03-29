package fc.ul.scrimfinder.rest.client;

import fc.ul.scrimfinder.dto.external.ExternalPlayerDTO;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/v1/riot/players")
@RegisterRestClient(configKey = "scrimfinder-external-api")
public interface ExternalPlayerClient {

    @GET
    @Path("/{name}/{tag}")
    ExternalPlayerDTO fetchPlayerRank(@PathParam("name") String name, @PathParam("tag") String tag);
}
