package fc.ul.scrimfinder.client;

import fc.ul.scrimfinder.dto.response.MatchDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "detail-filling-service")
@RegisterProvider(DetailFillingServiceExceptionMapper.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface DetailFillingClient {
    @GET
    @Path("/{matchId}")
    MatchDTO getFilledMatch(@PathParam("matchId") @NotBlank String matchId);
}
