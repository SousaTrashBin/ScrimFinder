package fc.ul.scrimfinder.client;

import fc.ul.scrimfinder.dto.external.PlayerRankingDTO;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/v1/ranking")
@RegisterRestClient(configKey = "ranking-service-api")
@RegisterProvider(RankingServiceExceptionMapper.class)
public interface RankingServiceClient {

    @GET
    @Path("/players/{playerId}/queue")
    @Produces(MediaType.APPLICATION_JSON)
    List<PlayerRankingDTO> getPlayerRanking(
            @PathParam("playerId") Long playerId, @QueryParam("queueId") Long queueId);

    @POST
    @Path("/queue/{queueId}")
    void createQueue(
            @PathParam("queueId") Long queueId,
            @QueryParam("name") String name,
            @QueryParam("initialMMR") int initialMMR);

    @POST
    @Path("/players")
    void registerPlayer(@QueryParam("id") Long id, @QueryParam("username") String username);

    @PUT
    @Path("/players/{playerId}/link-lol-account")
    void linkLolAccount(
            @PathParam("playerId") Long playerId,
            @QueryParam("puuid") String puuid,
            @QueryParam("gameName") String gameName,
            @QueryParam("tagLine") String tagLine,
            @QueryParam("region") fc.ul.scrimfinder.util.Region region);

    @PUT
    @Path("/players/{playerId}/set-primary-account")
    void setPrimaryAccount(@PathParam("playerId") Long playerId, @QueryParam("puuid") String puuid);

    @POST
    @Path("/players/{playerId}/sync-mmr")
    void syncMmr(@PathParam("playerId") Long playerId);
}
