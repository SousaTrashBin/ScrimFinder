package fc.ul.scrimfinder;

import fc.ul.scrimfinder.domain.Queue;
import fc.ul.scrimfinder.repository.QueueRepository;
import fc.ul.scrimfinder.util.MatchmakingMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class Startup {

    @Inject QueueRepository queueRepository;

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        if (queueRepository.findByIdOptional(1L).isEmpty()) {
            Queue globalQueue = new Queue();
            globalQueue.setId(1L);
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
