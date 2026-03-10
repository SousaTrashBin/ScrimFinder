package fc.ul.scrimfinder.mapper;

import fc.ul.scrimfinder.domain.Match;
import fc.ul.scrimfinder.dto.request.MatchAddDto;
import fc.ul.scrimfinder.dto.response.matchfull.MatchFullDto;
import fc.ul.scrimfinder.dto.response.MatchSimplifiedDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface MatchMapper {
    MatchAddDto matchEntityToMatchAddDto(Match match);

    MatchSimplifiedDto matchEntityToMatchSimplifiedDto(Match match);

    MatchFullDto matchEntityToMatchFullDto(Match match);

    Match matchAddDtoToMatch(MatchAddDto matchAddDto);

    Match matchSimplifiedDtoToMatch(MatchSimplifiedDto matchSimplifiedDto);

    Match matchFullDtoToMatch(MatchFullDto matchFullDto);
}
