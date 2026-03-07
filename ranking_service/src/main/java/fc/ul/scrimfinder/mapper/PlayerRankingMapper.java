package fc.ul.scrimfinder.mapper;

import fc.ul.scrimfinder.domain.PlayerRanking;
import fc.ul.scrimfinder.dto.response.PlayerRankingDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface PlayerRankingMapper {
    PlayerRankingDTO toDTO(PlayerRanking playerRanking);

    PlayerRanking toEntity(PlayerRankingDTO playerRankingDTO);
}
