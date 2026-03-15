package fc.ul.scrimfinder.client;

import fc.ul.scrimfinder.dto.request.MatchStatsDTO;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/v1/external")
@RegisterRestClient(configKey = "detail-filling-api")
@RegisterProvider(DetailFillingServiceExceptionMapper.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface DetailFillingClient {
    @GET
    @Path("/matches/{matchId}")
    MatchStatsDTO getMatchById(@PathParam("matchId") @Positive Long matchId);
}
