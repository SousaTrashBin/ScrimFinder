package fc.ul.scrimfinder.client;

import fc.ul.scrimfinder.dto.external.PlayerRankingDTO;
import fc.ul.scrimfinder.dto.request.MatchResultRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/v1/ranking")
@RegisterRestClient(configKey = "ranking-service-api")
public interface RankingServiceClient {

    @GET
    @Path("/players/{playerId}/queue")
    @Produces(MediaType.APPLICATION_JSON)
    PlayerRankingDTO getPlayerRanking(@PathParam("playerId") Long playerId, @QueryParam("queueId") Long queueId);

    @POST
    @Path("/players/matches/results")
    @Consumes(MediaType.APPLICATION_JSON)
    void reportMatchResults(MatchResultRequest matchResultRequest);

    @POST
    @Path("/queue/{queueId}")
    void createQueue(@PathParam("queueId") Long queueId,
                     @QueryParam("name") String name,
                     @QueryParam("initialMMR") int initialMMR);

    @POST
    @Path("/players")
    void registerPlayer(@QueryParam("username") String username);

    @PUT
    @Path("/players/{playerId}/link-lol-account")
    void linkLolAccount(@PathParam("playerId") Long playerId, @QueryParam("lolAccountId") String lolAccountId);

    @POST
    @Path("/players/{playerId}/sync-mmr")
    void syncMmr(@PathParam("playerId") Long playerId);

}
