package fc.ul.scrimfinder.client;

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
    @Path("matches/{matchId}")
    String getFilledMatch(@PathParam("matchId") @NotBlank String matchId);

    @GET
    @Path("players/{name}/{tag}")
    String getFilledPlayer(
            @PathParam("name") @NotBlank String name, @PathParam("tag") @NotBlank String tag);
}
