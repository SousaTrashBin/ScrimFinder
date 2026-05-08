package fc.ul.scrimfinder.client;

import fc.ul.scrimfinder.dto.external.PlayerRankingDTO;
import fc.ul.scrimfinder.dto.request.LinkLolAccountRequest;
import fc.ul.scrimfinder.dto.request.SetPrimaryAccountRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/v1/ranking")
@RegisterRestClient(configKey = "ranking-service-api")
@RegisterProvider(RankingServiceExceptionMapper.class)
public interface RankingServiceClient {

    @GET
    @Path("/players/{playerId}/queue-rankings/{queueId}")
    @Produces(MediaType.APPLICATION_JSON)
    List<PlayerRankingDTO> getPlayerRanking(
            @PathParam("playerId") UUID playerId, @PathParam("queueId") UUID queueId);

    @PUT
    @Path("/players/{playerId}/link-lol-account")
    void linkLolAccount(@PathParam("playerId") UUID playerId, LinkLolAccountRequest request);

    @PUT
    @Path("/players/{playerId}/set-primary-account")
    void setPrimaryAccount(@PathParam("playerId") UUID playerId, SetPrimaryAccountRequest request);

    @POST
    @Path("/players/{playerId}/sync-mmr")
    void syncMmr(@PathParam("playerId") UUID playerId);
}
