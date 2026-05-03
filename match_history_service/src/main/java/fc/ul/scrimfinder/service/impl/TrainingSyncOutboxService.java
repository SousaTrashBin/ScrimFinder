package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.domain.TrainingSyncOutboxEvent;
import fc.ul.scrimfinder.repository.TrainingSyncOutboxRepository;
import fc.ul.scrimfinder.service.TrainingAdapterService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TrainingSyncOutboxService {
    private static final int MAX_RETRY_ATTEMPTS = 20;
    private static final int BATCH_SIZE = 50;

    @Inject TrainingSyncOutboxRepository outboxRepository;
    @Inject TrainingAdapterService trainingAdapterService;
    @Inject Logger logger;

    @Transactional
    public void enqueue(String riotMatchId) {
        TrainingSyncOutboxEvent event = new TrainingSyncOutboxEvent();
        event.setRiotMatchId(riotMatchId);
        event.setStatus("PENDING");
        event.setAttempts(0);
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        outboxRepository.persist(event);
    }

    @Transactional
    public boolean processSinglePending(String riotMatchId) {
        TrainingSyncOutboxEvent event =
                outboxRepository
                        .find("riotMatchId = ?1 and status = ?2 order by createdAt asc", riotMatchId, "PENDING")
                        .firstResult();
        if (event == null) {
            return true;
        }
        return processEvent(event);
    }

    @Scheduled(every = "15s")
    @Transactional
    void retryPending() {
        List<TrainingSyncOutboxEvent> pendingEvents =
                outboxRepository.findPendingEvents(LocalDateTime.now(), BATCH_SIZE);
        for (TrainingSyncOutboxEvent event : pendingEvents) {
            processEvent(event);
        }
    }

    private boolean processEvent(TrainingSyncOutboxEvent event) {
        boolean delivered = trainingAdapterService.sendMatchForAnalysis(event.getRiotMatchId());
        event.setAttempts(event.getAttempts() + 1);
        event.setUpdatedAt(LocalDateTime.now());

        if (delivered) {
            event.setStatus("SENT");
            event.setLastError(null);
            event.setNextAttemptAt(null);
            logger.infof("Outbox delivered match %s to training service", event.getRiotMatchId());
            return true;
        }

        if (event.getAttempts() >= MAX_RETRY_ATTEMPTS) {
            event.setStatus("DEAD_LETTER");
            event.setLastError("Max retry attempts reached");
            event.setNextAttemptAt(null);
            logger.errorf(
                    "Outbox moved match %s to DEAD_LETTER after %d attempts",
                    event.getRiotMatchId(), event.getAttempts());
            return false;
        }

        int backoffSeconds = Math.min(300, (int) Math.pow(2, Math.min(10, event.getAttempts())));
        event.setNextAttemptAt(LocalDateTime.now().plusSeconds(backoffSeconds));
        event.setLastError("Training service unavailable or rejected request");
        logger.warnf(
                "Outbox retry scheduled for match %s in %d seconds (attempt %d)",
                event.getRiotMatchId(), backoffSeconds, event.getAttempts());
        return false;
    }
}
