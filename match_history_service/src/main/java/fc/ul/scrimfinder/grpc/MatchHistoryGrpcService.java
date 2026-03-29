package fc.ul.scrimfinder.grpc;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.eclipse.microprofile.faulttolerance.Timeout;

@GrpcService
public class MatchHistoryGrpcService implements MatchHistoryService {

    @Inject fc.ul.scrimfinder.service.MatchHistoryService matchHistoryService;

    @Override
    @Timeout(2000)
    public Uni<SaveMatchMMRGainsResponse> saveMatchMMRGains(SaveMatchMMRGainsRequest request) {
        UUID queueId = UUID.fromString(request.getQueueId());
        Map<String, Integer> mmrGains =
                request.getPlayerMMRGainsMap().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        try {
            matchHistoryService.addMatchById(request.getGameId(), queueId, mmrGains);
            return Uni.createFrom()
                    .item(
                            SaveMatchMMRGainsResponse.newBuilder()
                                    .setSuccess(true)
                                    .setMessage("Match history and MMR gains saved successfully")
                                    .build());
        } catch (Exception e) {
            return Uni.createFrom()
                    .item(
                            SaveMatchMMRGainsResponse.newBuilder()
                                    .setSuccess(false)
                                    .setMessage("Error saving match history: " + e.getMessage())
                                    .build());
        }
    }
}
