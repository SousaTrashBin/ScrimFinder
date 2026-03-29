package fc.ul.scrimfinder.mapper;

import fc.ul.scrimfinder.domain.PlayerRanking;
import fc.ul.scrimfinder.dto.response.PlayerRankingDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi")
public interface PlayerRankingMapper {

    @Mapping(source = "player.id", target = "playerId")
    @Mapping(source = "player.discordUsername", target = "username")
    @Mapping(
            target = "lolAccountPPUID",
            expression =
                    "java(playerRanking.getPlayer().getPrimaryAccount() != null ? playerRanking.getPlayer().getPrimaryAccount().getPuuid() : null)")
    @Mapping(
            target = "region",
            expression =
                    "java(playerRanking.getPlayer().getPrimaryAccount() != null ? playerRanking.getPlayer().getPrimaryAccount().getRegion() : null)")
    @Mapping(source = "queue.id", target = "queueId")
    PlayerRankingDTO toDTO(PlayerRanking playerRanking);
}
