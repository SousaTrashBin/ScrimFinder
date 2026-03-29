package fc.ul.scrimfinder.mapper;

import fc.ul.scrimfinder.domain.Match;
import fc.ul.scrimfinder.domain.TeamMatchStats;
import fc.ul.scrimfinder.dto.request.TeamStatsDTO;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.exception.InvalidTeamsException;
import fc.ul.scrimfinder.util.TeamSide;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(
        componentModel = "jakarta-cdi",
        uses = {PlayerMatchStatsMapper.class, TeamMatchStatsMapper.class})
public abstract class MatchMapper {

    @jakarta.inject.Inject TeamMatchStatsMapper teamMatchStatsMapper;

    @Mappings({
        @Mapping(target = "players", source = "playerMatchStats"),
        @Mapping(
                target = "teams",
                expression = "java(blueRedTeamsToList(match.getBlue(), match.getRed()))")
    })
    public abstract MatchDTO matchToDto(Match match);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "playerMatchStats", source = "players"),
        @Mapping(
                target = "blue",
                expression = "java(getTeam(matchDTO.getTeams(), fc.ul.scrimfinder.util.TeamSide.BLUE))"),
        @Mapping(
                target = "red",
                expression = "java(getTeam(matchDTO.getTeams(), fc.ul.scrimfinder.util.TeamSide.RED))")
    })
    public abstract Match dtoToMatch(MatchDTO matchDTO);

    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "playerMatchStats", ignore = true),
        @Mapping(
                target = "blue",
                expression = "java(getTeam(matchDTO.getTeams(), fc.ul.scrimfinder.util.TeamSide.BLUE))"),
        @Mapping(
                target = "red",
                expression = "java(getTeam(matchDTO.getTeams(), fc.ul.scrimfinder.util.TeamSide.RED))")
    })
    public abstract Match dtoToMatchWithNoPlayerStats(MatchDTO matchDTO);

    List<TeamStatsDTO> blueRedTeamsToList(TeamMatchStats blueTeam, TeamMatchStats redTeam) {
        return List.of(
                teamMatchStatsMapper.teamMatchStatsToDTO(blueTeam),
                teamMatchStatsMapper.teamMatchStatsToDTO(redTeam));
    }

    TeamMatchStats getTeam(List<TeamStatsDTO> teams, TeamSide side) {
        return teamMatchStatsMapper.dtoToTeamMatchStats(
                teams.stream()
                        .filter(teamStatsDTO -> teamStatsDTO.side().equals(side))
                        .findFirst()
                        .orElseThrow(
                                () -> new InvalidTeamsException("Could not find team from side: " + side)));
    }
}
