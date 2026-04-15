package fc.ul.scrimfinder;

import fc.ul.scrimfinder.domain.Queue;
import fc.ul.scrimfinder.repository.QueueRepository;
import fc.ul.scrimfinder.util.MatchmakingMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;

@ApplicationScoped
public class Startup {

    private static final UUID GLOBAL_QUEUE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Inject QueueRepository queueRepository;

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        if (queueRepository.findByIdOptional(GLOBAL_QUEUE_ID).isEmpty()) {
            Queue globalQueue = new Queue();
            globalQueue.setId(GLOBAL_QUEUE_ID);
            globalQueue.setName("Global Queue");
            globalQueue.setNamespace("GLOBAL");
            globalQueue.setRequiredPlayers(10);
            globalQueue.setRoleQueue(false);
            globalQueue.setMode(MatchmakingMode.RANK_BASED);
            globalQueue.setMmrWindow(200);
            queueRepository.persist(globalQueue);
        }
    }
}
