package fc.ul.scrimfinder;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

@OpenAPIDefinition(
        info = @Info(
                title = "Match History",
                version = "0.0.1"
        )
)
@ApplicationPath("/api/v1/history")
public class EntryPoint extends Application {
}
