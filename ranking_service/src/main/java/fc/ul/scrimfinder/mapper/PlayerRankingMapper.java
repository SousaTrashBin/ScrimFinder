package fc.ul.scrimfinder.mapper;

import fc.ul.scrimfinder.domain.PlayerRanking;
import fc.ul.scrimfinder.dto.response.PlayerRankingDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi")
public interface PlayerRankingMapper {

    @Mapping(source = "player.id", target = "playerId")
    @Mapping(source = "player.discordUsername", target = "username")
    @Mapping(source = "player.lolAccountPPUID", target = "lolAccountPPUID")
    @Mapping(source = "queue.id", target = "queueId")
    PlayerRankingDTO toDTO(PlayerRanking playerRanking);

    @Mapping(source = "playerId", target = "player.id")
    @Mapping(source = "username", target = "player.discordUsername")
    @Mapping(source = "lolAccountPPUID", target = "player.lolAccountPPUID")
    @Mapping(source = "queueId", target = "queue.id")
    @Mapping(target = "privateId", ignore = true)
    PlayerRanking toEntity(PlayerRankingDTO playerRankingDTO);
}
