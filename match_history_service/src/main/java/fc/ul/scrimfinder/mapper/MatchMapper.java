package fc.ul.scrimfinder.mapper;

import fc.ul.scrimfinder.domain.Match;
import fc.ul.scrimfinder.dto.request.MatchAddDto;
import fc.ul.scrimfinder.dto.response.MatchDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface MatchMapper {
    MatchAddDto matchEntityToMatchAddDto(Match match);

    MatchDto matchEntityToMatchSimplifiedDto(Match match);

    Match matchAddDtoToMatch(MatchAddDto matchAddDto);

    Match matchSimplifiedDtoToMatch(MatchDto matchDto);
}
