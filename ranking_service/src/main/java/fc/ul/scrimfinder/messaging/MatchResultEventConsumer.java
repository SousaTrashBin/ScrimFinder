package fc.ul.scrimfinder.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import fc.ul.scrimfinder.dto.internal.MatchResultEvent;
import fc.ul.scrimfinder.dto.request.MatchResultRequest;
import fc.ul.scrimfinder.service.PlayerRankingService;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Slf4j
@ApplicationScoped
public class MatchResultEventConsumer {

    @Inject ObjectMapper objectMapper;
    @Inject PlayerRankingService playerRankingService;

    @Incoming("match-results-events")
    @Blocking
    public void onMessage(String payload) {
        try {
            MatchResultEvent event = objectMapper.readValue(payload, MatchResultEvent.class);
            Map<String, MatchResultRequest.PlayerDelta> deltas =
                    event.playerDeltas().entrySet().stream()
                            .collect(
                                    Collectors.toMap(
                                            Map.Entry::getKey,
                                            e ->
                                                    new MatchResultRequest.PlayerDelta(
                                                            e.getValue().winDelta(), e.getValue().lossDelta())));

            playerRankingService.processMatchResults(
                    new MatchResultRequest(event.gameId(), UUID.fromString(event.queueId()), deltas));
        } catch (Exception e) {
            log.error("Failed to process RabbitMQ match-result event", e);
            throw new RuntimeException("Failed to process match-result event", e);
        }
    }
}
