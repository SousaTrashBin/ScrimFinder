package fc.ul.scrimfinder.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class ReadinessRiotApiCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        if (isRiotApiAvailable()) {
            return HealthCheckResponse.named("RiotApiAvailabilityCheck").up().build();
        }
        return HealthCheckResponse.named("RiotApiAvailabilityCheck").down().build();
    }

    private boolean isRiotApiAvailable() {
        // TODO - Quick external endpoint check
        return true;
    }
}
