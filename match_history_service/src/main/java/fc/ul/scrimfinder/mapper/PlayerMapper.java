package fc.ul.scrimfinder.mapper;

import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.dto.request.PlayerDTO;
import fc.ul.scrimfinder.dto.request.PlayerStatsDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(
        componentModel = "jakarta-cdi",
        uses = {PlayerMatchStatsMapper.class})
public interface PlayerMapper {
    PlayerDTO playerToDTO(Player player);

    @Mapping(target = "id", ignore = true)
    Player dtoToPlayer(PlayerDTO playerDTO);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "puuid", source = "playerStatsDTO.riotId.puuid"),
        @Mapping(target = "name", source = "playerStatsDTO.riotId.playerName"),
        @Mapping(target = "tag", source = "playerStatsDTO.riotId.playerTag"),
        @Mapping(target = "playerMatchStats", ignore = true)
    })
    Player playerMatchStatsDTOToPlayer(PlayerStatsDTO playerStatsDTO);
}
