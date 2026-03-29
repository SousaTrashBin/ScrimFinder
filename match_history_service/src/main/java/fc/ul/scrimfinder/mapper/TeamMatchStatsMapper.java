package fc.ul.scrimfinder.mapper;

import fc.ul.scrimfinder.domain.TeamMatchStats;
import fc.ul.scrimfinder.dto.request.TeamStatsDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jakarta-cdi")
public interface TeamMatchStatsMapper {
    TeamStatsDTO teamMatchStatsToDTO(TeamMatchStats teamMatchStats);

    TeamMatchStats dtoToTeamMatchStats(TeamStatsDTO teamStatsDTO);
}
