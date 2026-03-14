package fc.ul.scrimfinder.mapper;

import fc.ul.scrimfinder.domain.Match;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "jakarta")
public interface MatchMapper {
    @Mapping(source = "lobby.id", target = "lobbyId")
    MatchDTO toDTO(Match match);
}
