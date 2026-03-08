package fc.ul.scrimfinder.mapper;

import fc.ul.scrimfinder.domain.MatchTicket;
import fc.ul.scrimfinder.dto.response.MatchTicketDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "jakarta")
public interface MatchTicketMapper {
    @Mapping(source = "player.id", target = "playerId")
    @Mapping(source = "queue.id", target = "queueId")
    @Mapping(source = "lobby.id", target = "lobbyId")
    MatchTicketDTO toDTO(MatchTicket ticket);
}
