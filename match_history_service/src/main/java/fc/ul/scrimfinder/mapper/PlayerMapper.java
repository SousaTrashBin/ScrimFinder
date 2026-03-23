package fc.ul.scrimfinder.mapper;

import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.dto.request.PlayerDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
        componentModel = "jakarta-cdi",
        uses = {PlayerMatchStatsMapper.class})
public interface PlayerMapper {
    PlayerDTO playerToDTO(Player player);

    @Mapping(target = "id", ignore = true)
    Player dtoToPlayer(PlayerDTO playerDTO);
}
