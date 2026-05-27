package fc.ul.scrimfinder.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.net.http.HttpConnectTimeoutException;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "detail-filling-service")
@RegisterProvider(DetailFillingServiceExceptionMapper.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface DetailFillingClient {
    @GET
    @Path("matches/{matchId}")
    @Retry(maxRetries = 4)
    @CircuitBreaker(failOn = HttpConnectTimeoutException.class)
    String getFilledMatch(@PathParam("matchId") @NotBlank String matchId);

    @GET
    @Path("players/{server}/{name}/{tag}")
    @Retry(maxRetries = 4)
    @CircuitBreaker(failOn = HttpConnectTimeoutException.class)
    String getFilledPlayer(
            @PathParam("server") @NotBlank String server,
            @PathParam("name") @NotBlank String name,
            @PathParam("tag") @NotBlank String tag);
}
