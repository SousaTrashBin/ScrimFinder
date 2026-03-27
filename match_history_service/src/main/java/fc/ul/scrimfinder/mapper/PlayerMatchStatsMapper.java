package fc.ul.scrimfinder.mapper;

import fc.ul.scrimfinder.domain.Match;
import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.domain.PlayerMatchStats;
import fc.ul.scrimfinder.dto.request.PlayerStatsDTO;
import fc.ul.scrimfinder.service.DetailFillingAdapterService;
import fc.ul.scrimfinder.util.RiotId;
import jakarta.inject.Inject;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(componentModel = "jakarta-cdi")
public abstract class PlayerMatchStatsMapper {

    @Inject DetailFillingAdapterService detailFillingAdapterService;

    @Mappings({
        @Mapping(target = "riotId", expression = "java(buildRiotId(playerMatchStats.getPlayer()))"),
        @Mapping(target = "side", source = "teamSide")
    })
    public abstract PlayerStatsDTO playerMatchStatsToDTO(PlayerMatchStats playerMatchStats);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "match", ignore = true),
        @Mapping(target = "player", ignore = true),
        @Mapping(target = "teamSide", source = "side")
    })
    public abstract PlayerMatchStats dtoToPlayerMatchStats(PlayerStatsDTO playerStatsDTO);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "teamSide", source = "playerStatsDTO.side")
    })
    public abstract PlayerMatchStats dtoToFullPlayerMatchStats(
            PlayerStatsDTO playerStatsDTO, Match match, Player player);

    RiotId buildRiotId(Player player) {
        return new RiotId(
                player.getPuuid(),
                player.getName(),
                player.getTag(),
                detailFillingAdapterService.getPlayerIcon(player.getName(), player.getTag()));
    }
}
