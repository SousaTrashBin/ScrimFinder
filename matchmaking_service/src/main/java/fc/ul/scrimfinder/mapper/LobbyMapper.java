package fc.ul.scrimfinder.mapper;

import fc.ul.scrimfinder.domain.Lobby;
import fc.ul.scrimfinder.dto.response.LobbyDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "jakarta", uses = {MatchTicketMapper.class})
public interface LobbyMapper {
    @Mapping(source = "queue.id", target = "queueId")
    LobbyDTO toDTO(Lobby lobby);
}
