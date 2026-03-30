package fc.ul.scrimfinder.client;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.core.UriBuilder;

@ApplicationScoped
public class ClientUrlPrefixProvider implements ClientRequestFilter, ClientResponseFilter {
    private static final ThreadLocal<String> PREFIX = new ThreadLocal<>();

    public void setPrefix(String prefix) {
        PREFIX.set(prefix);
    }

    @Override
    public void filter(ClientRequestContext requestContext) {
        String prefix = PREFIX.get();
        if (prefix != null) {
            requestContext.setUri(
                    UriBuilder.fromUri(requestContext.getUri())
                            .host(String.format("%s.%s", prefix, requestContext.getUri().getHost()))
                            .build());
        }
    }

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) {
        PREFIX.remove();
    }
}
