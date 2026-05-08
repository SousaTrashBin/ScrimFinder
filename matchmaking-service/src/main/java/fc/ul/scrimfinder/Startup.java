package fc.ul.scrimfinder;

import fc.ul.scrimfinder.domain.Queue;
import fc.ul.scrimfinder.repository.QueueRepository;
import fc.ul.scrimfinder.util.MatchmakingMode;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.UUID;
import org.jboss.logging.Logger;

@ApplicationScoped
public class Startup {

    private static final Logger LOG = Logger.getLogger(Startup.class);
    private static final UUID GLOBAL_QUEUE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Inject QueueRepository queueRepository;

    private volatile boolean globalQueueSeeded;

    void onStart(@Observes StartupEvent ev) {
        trySeedGlobalQueue();
    }

    @Scheduled(every = "15s", delayed = "10s")
    void ensureGlobalQueueSeeded() {
        if (!globalQueueSeeded) {
            trySeedGlobalQueue();
        }
    }

    private void trySeedGlobalQueue() {
        try {
            QuarkusTransaction.requiringNew()
                    .run(
                            () -> {
                                if (queueRepository.findByIdOptional(GLOBAL_QUEUE_ID).isPresent()) {
                                    globalQueueSeeded = true;
                                    return;
                                }

                                Queue globalQueue = new Queue();
                                globalQueue.setId(GLOBAL_QUEUE_ID);
                                globalQueue.setName("Global Queue");
                                globalQueue.setNamespace("GLOBAL");
                                globalQueue.setRequiredPlayers(10);
                                globalQueue.setRoleQueue(false);
                                globalQueue.setMode(MatchmakingMode.RANK_BASED);
                                globalQueue.setMmrWindow(200);
                                queueRepository.persist(globalQueue);
                                queueRepository.getEntityManager().flush();
                                globalQueueSeeded = true;
                            });
            LOG.info("Global queue seeded.");
        } catch (RuntimeException ex) {
            LOG.info("Queue table not ready yet; global queue seeding deferred.");
        }
    }
}
