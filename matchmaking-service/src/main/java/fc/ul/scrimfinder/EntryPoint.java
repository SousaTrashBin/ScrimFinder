package fc.ul.scrimfinder;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

@OpenAPIDefinition(info = @Info(title = "Matchmaking Service", version = "0.0.1"))
@ApplicationPath("/")
public class EntryPoint extends Application {}
