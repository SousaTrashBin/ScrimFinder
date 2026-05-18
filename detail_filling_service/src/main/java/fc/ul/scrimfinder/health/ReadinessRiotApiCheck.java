package fc.ul.scrimfinder.health;

import fc.ul.scrimfinder.client.ClientUrlPrefixProvider;
import fc.ul.scrimfinder.client.RiotHealthClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Readiness
@ApplicationScoped
public class ReadinessRiotApiCheck implements HealthCheck {

    @Inject @RestClient RiotHealthClient riotHealthClient;

    @Inject ClientUrlPrefixProvider clientUrlPrefixProvider;

    @Override
    public HealthCheckResponse call() {
        clientUrlPrefixProvider.setPrefix("euw1"); // Use EUW1 as default health check region
        if (isRiotApiAvailable()) {
            return HealthCheckResponse.named("Riot API availability check").up().build();
        }
        return HealthCheckResponse.named("Riot API availability check").down().build();
    }

    private boolean isRiotApiAvailable() {
        clientUrlPrefixProvider.setPrefix("euw1");
        try (Response response = riotHealthClient.checkHealth()) {
            return response.getStatus() < 400;
        } catch (Exception x) {
            return false;
        }
    }
}
