package fc.ul.scrimfinder.grpc;

import io.quarkus.grpc.GrpcService;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.eclipse.microprofile.faulttolerance.Timeout;

@GrpcService
@Blocking
public class MatchHistoryGrpcService implements MatchHistoryService {

    @Inject fc.ul.scrimfinder.service.MatchHistoryService matchHistoryService;

    @Override
    @Timeout(5000)
    public Uni<AddMatchResponse> addMatch(AddMatchRequest request) {
        UUID queueId = UUID.fromString(request.getQueueId());
        Map<String, Integer> mmrGains =
                request.getPlayerMMRGainsMap().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        try {
            matchHistoryService.addMatchById(request.getGameId(), queueId, mmrGains);
            return Uni.createFrom()
                    .item(
                            AddMatchResponse.newBuilder()
                                    .setSuccess(true)
                                    .setMessage("Match history and MMR gains saved successfully")
                                    .build());
        } catch (Exception e) {
            return Uni.createFrom()
                    .item(
                            AddMatchResponse.newBuilder()
                                    .setSuccess(false)
                                    .setMessage("Error saving match history: " + e.getMessage())
                                    .build());
        }
    }

    @Override
    @Timeout(5000)
    public Uni<DeleteMatchResponse> deleteMatch(DeleteMatchRequest request) {
        try {
            matchHistoryService.deleteMatchById(request.getGameId());
            return Uni.createFrom()
                    .item(
                            DeleteMatchResponse.newBuilder()
                                    .setSuccess(true)
                                    .setMessage("Match history deleted successfully")
                                    .build());
        } catch (Exception e) {
            return Uni.createFrom()
                    .item(
                            DeleteMatchResponse.newBuilder()
                                    .setSuccess(false)
                                    .setMessage("Error deleting match history: " + e.getMessage())
                                    .build());
        }
    }
}
