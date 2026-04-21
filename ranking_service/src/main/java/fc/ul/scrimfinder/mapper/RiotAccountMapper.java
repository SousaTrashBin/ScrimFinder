package fc.ul.scrimfinder.mapper;

import fc.ul.scrimfinder.domain.RiotAccount;
import fc.ul.scrimfinder.dto.response.RiotAccountDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface RiotAccountMapper {

    @org.mapstruct.Mapping(source = "primary", target = "isPrimary")
    RiotAccountDTO toDTO(RiotAccount riotAccount);
}
