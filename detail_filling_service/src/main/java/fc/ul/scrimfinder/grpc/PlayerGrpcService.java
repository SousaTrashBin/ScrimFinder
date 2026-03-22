package fc.ul.scrimfinder.grpc;

import fc.ul.scrimfinder.client.RiotAccountServiceClient;
import fc.ul.scrimfinder.dto.response.player.PlayerDTO;
import fc.ul.scrimfinder.service.RiotAdapterService;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.util.stream.Collectors;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@GrpcService
public class PlayerGrpcService implements ExternalPlayerFillingService {

    @Inject RiotAdapterService riotAdapterService;

    @Inject @RestClient RiotAccountServiceClient accountServiceClient;

    @Override
    public Uni<PlayerResponse> getPlayer(PlayerRequest request) {
        return Uni.createFrom()
                .item(
                        () -> {
                            PlayerDTO player =
                                    riotAdapterService.getPlayerData(request.getGameName(), request.getTagLine());

                            return PlayerResponse.newBuilder()
                                    .setPuuid(player.account().puuid())
                                    .setGameName(player.account().name())
                                    .setTagLine(player.account().tag())
                                    .addAllEntries(
                                            player.queues().stream()
                                                    .map(
                                                            entry ->
                                                                    LeagueEntry.newBuilder()
                                                                            .setQueueType(
                                                                                    entry.queueType() != null ? entry.queueType() : "")
                                                                            .setTier(
                                                                                    entry.rank().tier() != null
                                                                                            ? entry.rank().tier().getTier()
                                                                                            : "")
                                                                            .setRank(
                                                                                    entry.rank().division() != null
                                                                                            ? entry.rank().division().toString()
                                                                                            : "")
                                                                            .setLeaguePoints(
                                                                                    entry.rank().lps() != null ? entry.rank().lps() : 0)
                                                                            .setWins(entry.wins() != null ? entry.wins() : 0)
                                                                            .setLosses(entry.losses() != null ? entry.losses() : 0)
                                                                            .build())
                                                    .collect(Collectors.toList()))
                                    .build();
                        });
    }
}
