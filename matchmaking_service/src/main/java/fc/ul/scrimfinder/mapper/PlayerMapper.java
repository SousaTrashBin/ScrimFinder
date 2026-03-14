package fc.ul.scrimfinder.mapper;

import fc.ul.scrimfinder.domain.Player;
import fc.ul.scrimfinder.dto.response.PlayerDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jakarta")
public interface PlayerMapper {
    PlayerDTO toDTO(Player player);
}
