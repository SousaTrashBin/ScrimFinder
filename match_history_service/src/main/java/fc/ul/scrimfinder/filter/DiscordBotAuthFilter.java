package fc.ul.scrimfinder.filter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * DiscordBotAuthFilter
 *
 * <p>Validates requests originating from the Discord bot using HMAC-SHA256.
 *
 * <p>Required headers on every bot request: X-Bot-Signature — HexFormat HMAC-SHA256(secret,
 * accountId + ":" + timestamp) X-Account-Id — The Discord account ID the action is performed for
 * X-Timestamp — Unix epoch seconds (UTC) of when the request was signed
 *
 * <p>Requests without these headers are passed through untouched — normal client requests
 * (validated by Istio JWT policy) do not carry bot headers.
 *
 * <p>Replay protection: requests older than REPLAY_WINDOW_SECONDS are rejected.
 *
 * <p>Config property: scrimfinder.discord.bot-secret (injected via K8s secret, mapped to
 * DISCORD_BOT_SECRET env var in application.properties).
 */
@Slf4j
@ApplicationScoped
public class DiscordBotAuthFilter {

    private static final String HEADER_SIGNATURE = "X-Bot-Signature";
    private static final String HEADER_ACCOUNT_ID = "X-Account-Id";
    private static final String HEADER_TIMESTAMP = "X-Timestamp";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long REPLAY_WINDOW_SECONDS = 30L;

    @ConfigProperty(name = "scrimfinder.discord.bot-secret")
    String botSecret;

    @ServerRequestFilter
    public void filter(ContainerRequestContext ctx) {
        String signature = ctx.getHeaderString(HEADER_SIGNATURE);
        String accountId = ctx.getHeaderString(HEADER_ACCOUNT_ID);
        String timestamp = ctx.getHeaderString(HEADER_TIMESTAMP);

        // Not a bot request — let Istio JWT policy handle it
        if (signature == null && accountId == null && timestamp == null) {
            return;
        }

        // If any bot header is present, all three must be present
        if (signature == null || accountId == null || timestamp == null) {
            log.warn("Incomplete bot auth headers from {}", ctx.getUriInfo().getRequestUri());
            abort(ctx, "Missing bot authentication header(s).");
            return;
        }

        // Replay protection — reject stale requests
        long requestEpoch;
        try {
            requestEpoch = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            abort(ctx, "Invalid X-Timestamp value.");
            return;
        }

        long nowEpoch = Instant.now().getEpochSecond();
        if (Math.abs(nowEpoch - requestEpoch) > REPLAY_WINDOW_SECONDS) {
            log.warn("Stale bot request: timestamp={} now={}", requestEpoch, nowEpoch);
            abort(ctx, "Request timestamp outside replay window.");
            return;
        }

        // HMAC-SHA256 verification
        String payload = accountId + ":" + timestamp;
        String expected = hmac(payload);

        if (!constantTimeEquals(expected, signature)) {
            log.warn("Bot HMAC mismatch for accountId={}", accountId);
            abort(ctx, "Invalid bot signature.");
            return;
        }

        log.debug("Bot request authenticated for accountId={}", accountId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(botSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }

    /**
     * Constant-time string comparison to prevent timing attacks. Compares hex strings character by
     * character without short-circuiting.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    private static void abort(ContainerRequestContext ctx, String message) {
        ctx.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                        .entity("{\"code\":\"BOT_AUTH_FAILED\",\"message\":\"" + message + "\"}")
                        .header("Content-Type", "application/json")
                        .build());
    }
}
