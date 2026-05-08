package fc.ul.scrimfinder.grpc;

import fc.ul.scrimfinder.dto.request.CreatePlayerRequest;
import fc.ul.scrimfinder.service.PlayerRankingService;
import fc.ul.scrimfinder.service.PlayerService;
import fc.ul.scrimfinder.service.QueueService;
import fc.ul.scrimfinder.util.MMRRuleType;
import io.quarkus.grpc.GrpcService;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@GrpcService
@Blocking
public class RankingGrpcService implements RankingService {

    @Inject PlayerRankingService playerRankingService;
    @Inject PlayerService playerService;
    @Inject QueueService queueService;

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

    @Override
    public Uni<RegisterPlayerResponse> registerPlayer(RegisterPlayerRequest request) {
        try {
            playerService.createPlayer(UUID.fromString(request.getPlayerId()), request.getUsername());
            return Uni.createFrom()
                    .item(
                            RegisterPlayerResponse.newBuilder()
                                    .setSuccess(true)
                                    .setMessage("Player registered successfully")
                                    .build());
        } catch (Exception e) {
            return Uni.createFrom()
                    .item(
                            RegisterPlayerResponse.newBuilder()
                                    .setSuccess(false)
                                    .setMessage("Error registering player: " + e.getMessage())
                                    .build());
        }
    }

    @Override
    public Uni<CreateQueueResponse> createQueue(CreateQueueRequest request) {
        try {
            queueService.createQueue(
                    UUID.fromString(request.getQueueId()),
                    request.getName(),
                    MMRRuleType.NONE,
                    request.getInitialMMR());
            return Uni.createFrom()
                    .item(
                            CreateQueueResponse.newBuilder()
                                    .setSuccess(true)
                                    .setMessage("Queue created successfully")
                                    .build());
        } catch (Exception e) {
            return Uni.createFrom()
                    .item(
                            CreateQueueResponse.newBuilder()
                                    .setSuccess(false)
                                    .setMessage("Error creating queue: " + e.getMessage())
                                    .build());
        }
    }

    @Override
    public Uni<InitializePlayerMMRResponse> initializePlayerMMR(InitializePlayerMMRRequest request) {
        try {
            playerRankingService.populatePlayerMMR(
                    UUID.fromString(request.getPlayerId()),
                    new CreatePlayerRequest(Optional.of(UUID.fromString(request.getQueueId()))));
            return Uni.createFrom()
                    .item(
                            InitializePlayerMMRResponse.newBuilder()
                                    .setSuccess(true)
                                    .setMessage("Player MMR initialized successfully")
                                    .build());
        } catch (Exception e) {
            return Uni.createFrom()
                    .item(
                            InitializePlayerMMRResponse.newBuilder()
                                    .setSuccess(false)
                                    .setMessage("Error initializing player MMR: " + e.getMessage())
                                    .build());
        }
    }
}
