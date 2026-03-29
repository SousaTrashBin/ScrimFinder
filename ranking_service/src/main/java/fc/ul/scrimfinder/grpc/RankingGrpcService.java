package fc.ul.scrimfinder.grpc;

import fc.ul.scrimfinder.service.PlayerRankingService;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@GrpcService
public class RankingGrpcService implements RankingService {

    @Inject PlayerRankingService playerRankingService;

    @Override
    public Uni<MatchResultResponse> reportMatchResults(MatchResultRequest request) {
        Map<String, fc.ul.scrimfinder.dto.request.MatchResultRequest.PlayerDelta> deltas =
                new HashMap<>();
        request
                .getPlayerDeltasMap()
                .forEach(
                        (puuid, delta) -> {
                            deltas.put(
                                    puuid,
                                    new fc.ul.scrimfinder.dto.request.MatchResultRequest.PlayerDelta(
                                            delta.getWinDelta(), delta.getLossDelta()));
                        });

        fc.ul.scrimfinder.dto.request.MatchResultRequest dtoRequest =
                new fc.ul.scrimfinder.dto.request.MatchResultRequest(
                        request.getGameId(), UUID.fromString(request.getQueueId()), deltas);

        try {
            playerRankingService.processMatchResults(dtoRequest);
            return Uni.createFrom()
                    .item(
                            MatchResultResponse.newBuilder()
                                    .setSuccess(true)
                                    .setMessage("Match results processed successfully")
                                    .build());
        } catch (Exception e) {
            return Uni.createFrom()
                    .item(
                            MatchResultResponse.newBuilder()
                                    .setSuccess(false)
                                    .setMessage("Error processing match results: " + e.getMessage())
                                    .build());
        }
    }
}
