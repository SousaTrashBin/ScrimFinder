package fc.ul.scrimfinder.mapper;

import fc.ul.scrimfinder.domain.RiotAccount;
import fc.ul.scrimfinder.dto.response.RiotAccountDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface RiotAccountMapper {

    RiotAccountDTO toDTO(RiotAccount riotAccount);
}
