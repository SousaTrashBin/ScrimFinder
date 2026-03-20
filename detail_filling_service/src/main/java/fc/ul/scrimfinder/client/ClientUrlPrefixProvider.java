package fc.ul.scrimfinder.client;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.UriBuilder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ApplicationScoped
public class ClientUrlPrefixProvider implements ClientRequestFilter {
    String prefix;

    @Override
    public void filter(ClientRequestContext requestContext) {
        requestContext.setUri(
                UriBuilder.fromUri(
                                String.format("https://%s.%s", prefix, requestContext.getUri().toString()))
                        .build());
    }
}
