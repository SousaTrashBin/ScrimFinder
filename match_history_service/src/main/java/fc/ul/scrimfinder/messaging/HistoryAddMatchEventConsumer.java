package fc.ul.scrimfinder.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import fc.ul.scrimfinder.dto.internal.HistoryAddMatchEvent;
import fc.ul.scrimfinder.exception.MatchAlreadyExistsException;
import fc.ul.scrimfinder.service.MatchHistoryService;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Slf4j
@ApplicationScoped
public class HistoryAddMatchEventConsumer {

    @Inject ObjectMapper objectMapper;
    @Inject MatchHistoryService matchHistoryService;

    @Incoming("history-add-match-events")
    @Blocking
    public void onMessage(String payload) {
        try {
            HistoryAddMatchEvent event = objectMapper.readValue(payload, HistoryAddMatchEvent.class);
            matchHistoryService.addMatchById(
                    event.gameId(), UUID.fromString(event.queueId()), event.playerMmrGains());
        } catch (MatchAlreadyExistsException alreadyExists) {
            log.info("Ignoring duplicate history add-match event");
        } catch (Exception e) {
            log.error("Failed to process RabbitMQ history-add-match event", e);
            throw new RuntimeException("Failed to process history-add-match event", e);
        }
    }
}
