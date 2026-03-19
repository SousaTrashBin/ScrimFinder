package fc.ul.scrimfinder.mapper;

import fc.ul.scrimfinder.domain.Match;
import fc.ul.scrimfinder.dto.request.MatchAddDTO;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface MatchMapper {
    MatchDTO matchToDto(Match match);

    Match matchAddDtoToMatch(MatchAddDTO matchAddDto);
}
