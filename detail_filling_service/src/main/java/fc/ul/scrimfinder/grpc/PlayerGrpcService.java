package fc.ul.scrimfinder.grpc;

import fc.ul.scrimfinder.client.RiotAccountServiceClient;
import fc.ul.scrimfinder.dto.response.player.AccountDTO;
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
                            AccountDTO account =
                                    accountServiceClient.getByRiotId(request.getGameName(), request.getTagLine());
                            PlayerDTO player =
                                    riotAdapterService.getPlayerData(request.getGameName(), request.getTagLine());

                            return PlayerResponse.newBuilder()
                                    .setPuuid(account.puuid())
                                    .setGameName(account.gameName())
                                    .setTagLine(account.tagLine())
                                    .addAllEntries(
                                            player.entries().stream()
                                                    .map(
                                                            entry ->
                                                                    LeagueEntry.newBuilder()
                                                                            .setQueueType(
                                                                                    entry.queueType() != null ? entry.queueType() : "")
                                                                            .setTier(entry.tier() != null ? entry.tier() : "")
                                                                            .setRank(entry.rank() != null ? entry.rank() : "")
                                                                            .setLeaguePoints(
                                                                                    entry.leaguePoints() != null ? entry.leaguePoints() : 0)
                                                                            .setWins(entry.wins() != null ? entry.wins() : 0)
                                                                            .setLosses(entry.losses() != null ? entry.losses() : 0)
                                                                            .build())
                                                    .collect(Collectors.toList()))
                                    .build();
                        });
    }
}
