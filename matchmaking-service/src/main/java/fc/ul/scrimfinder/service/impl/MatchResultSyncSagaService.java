package fc.ul.scrimfinder.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fc.ul.scrimfinder.domain.Match;
import fc.ul.scrimfinder.domain.MatchLifecycleEvent;
import fc.ul.scrimfinder.domain.MatchResultOutboxEvent;
import fc.ul.scrimfinder.dto.internal.MatchResultEvent;
import fc.ul.scrimfinder.repository.MatchLifecycleEventRepository;
import fc.ul.scrimfinder.repository.MatchRepository;
import fc.ul.scrimfinder.repository.MatchResultOutboxRepository;
import fc.ul.scrimfinder.util.MatchState;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@Slf4j
@ApplicationScoped
public class MatchResultSyncSagaService {
    private static final int MAX_RETRY_ATTEMPTS = 20;
    private static final int BATCH_SIZE = 50;

    @Inject MatchResultOutboxRepository outboxRepository;
    @Inject MatchLifecycleEventRepository lifecycleEventRepository;
    @Inject MatchRepository matchRepository;
    @Inject ObjectMapper objectMapper;

    @Inject
    @Channel("match-results-events")
    Emitter<String> matchResultsEmitter;

    @Transactional
    public void recordLifecycle(UUID matchId, String step, String status, String message) {
        MatchLifecycleEvent event = new MatchLifecycleEvent();
        event.setMatchId(matchId);
        event.setStep(step);
        event.setStatus(status);
        event.setMessage(message);
        event.setCreatedAt(LocalDateTime.now());
        lifecycleEventRepository.persist(event);
    }

    @Transactional
    public void enqueueIfMissing(
            Match match,
            Map<String, fc.ul.scrimfinder.dto.request.MatchResultRequest.PlayerDelta> deltas) {
        Optional<MatchResultOutboxEvent> existing = outboxRepository.findByMatchId(match.getId());
        if (existing.isPresent()) {
            if ("SENT".equals(existing.get().getStatus())) {
                return;
            }
            existing.get().setStatus("PENDING");
            existing.get().setNextAttemptAt(null);
            existing.get().setUpdatedAt(LocalDateTime.now());
            return;
        }

        MatchResultOutboxEvent outboxEvent = new MatchResultOutboxEvent();
        outboxEvent.setMatchId(match.getId());
        outboxEvent.setExternalGameId(match.getExternalGameId());
        outboxEvent.setQueueId(match.getLobby().getQueue().getId());
        outboxEvent.setPlayerDeltasJson(toJson(deltas));
        outboxEvent.setStatus("PENDING");
        outboxEvent.setAttempts(0);
        outboxEvent.setCreatedAt(LocalDateTime.now());
        outboxEvent.setUpdatedAt(LocalDateTime.now());
        outboxRepository.persist(outboxEvent);
    }

    @Transactional
    public boolean processNow(UUID matchId) {
        MatchResultOutboxEvent event = outboxRepository.findByMatchId(matchId).orElse(null);
        if (event == null || "SENT".equals(event.getStatus())) {
            return true;
        }
        return processEvent(event);
    }

    @Scheduled(every = "15s")
    @Transactional
    void retryPending() {
        for (MatchResultOutboxEvent event :
                outboxRepository.findPending(LocalDateTime.now(), BATCH_SIZE)) {
            processEvent(event);
        }
    }

    private boolean processEvent(MatchResultOutboxEvent event) {
        try {
            Map<String, fc.ul.scrimfinder.dto.request.MatchResultRequest.PlayerDelta> deltas =
                    fromJson(event.getPlayerDeltasJson());
            MatchResultEvent payload =
                    new MatchResultEvent(
                            event.getExternalGameId(),
                            event.getQueueId().toString(),
                            deltas.entrySet().stream()
                                    .collect(
                                            Collectors.toMap(
                                                    Map.Entry::getKey,
                                                    entry ->
                                                            new MatchResultEvent.PlayerDeltaEvent(
                                                                    entry.getValue().winDelta(), entry.getValue().lossDelta()))));
            matchResultsEmitter.send(objectMapper.writeValueAsString(payload));
            event.setAttempts(event.getAttempts() + 1);
            event.setUpdatedAt(LocalDateTime.now());

            event.setStatus("SENT");
            event.setLastError(null);
            event.setNextAttemptAt(null);
            markMatchCompleted(event.getMatchId());
            recordLifecycle(
                    event.getMatchId(), "RESULT_SYNC", "SUCCESS", "Ranking event published to RabbitMQ");
            return true;
        } catch (Exception e) {
            event.setAttempts(event.getAttempts() + 1);
            event.setUpdatedAt(LocalDateTime.now());
            return scheduleRetry(event, e.getMessage());
        }
    }

    private boolean scheduleRetry(MatchResultOutboxEvent event, String error) {
        if (event.getAttempts() >= MAX_RETRY_ATTEMPTS) {
            event.setStatus("DEAD_LETTER");
            event.setLastError(error);
            event.setNextAttemptAt(null);
            markMatchFailure(event.getMatchId());
            recordLifecycle(event.getMatchId(), "RESULT_SYNC", "DEAD_LETTER", error);
            return false;
        }
        int backoffSeconds = Math.min(300, (int) Math.pow(2, Math.min(10, event.getAttempts())));
        event.setStatus("PENDING");
        event.setLastError(error);
        event.setNextAttemptAt(LocalDateTime.now().plusSeconds(backoffSeconds));
        markMatchFailure(event.getMatchId());
        recordLifecycle(event.getMatchId(), "RESULT_SYNC", "RETRY_SCHEDULED", error);
        return false;
    }

    private void markMatchCompleted(UUID matchId) {
        Match match = matchRepository.findById(matchId);
        if (match == null) return;
        match.setState(MatchState.COMPLETED);
        if (match.getEndedAt() == null) {
            match.setEndedAt(LocalDateTime.now());
        }
        matchRepository.persist(match);
    }

    private void markMatchFailure(UUID matchId) {
        Match match = matchRepository.findById(matchId);
        if (match == null) return;
        match.setState(MatchState.RESULT_REPORTING_FAILED);
        matchRepository.persist(match);
    }

    private String toJson(
            Map<String, fc.ul.scrimfinder.dto.request.MatchResultRequest.PlayerDelta> deltas) {
        try {
            return objectMapper.writeValueAsString(deltas);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize match deltas", e);
        }
    }

    private Map<String, fc.ul.scrimfinder.dto.request.MatchResultRequest.PlayerDelta> fromJson(
            String json) {
        try {
            return objectMapper.readValue(
                    json,
                    new TypeReference<
                            Map<String, fc.ul.scrimfinder.dto.request.MatchResultRequest.PlayerDelta>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize match deltas", e);
        }
    }
}
