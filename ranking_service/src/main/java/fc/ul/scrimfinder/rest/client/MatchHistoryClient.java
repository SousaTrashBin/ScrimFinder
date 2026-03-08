package fc.ul.scrimfinder.rest.client;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.Map;

@Path("/api/v1/history")
@RegisterRestClient(configKey = "scrimfinder-match-history-api")
public interface MatchHistoryClient {

    @POST
    @Path("/matches/{gameId}")
    void saveBatchMMRGains(
            @PathParam("gameId") String gameId,
            Map<Long, Integer> playerMMRGains
    );
}
