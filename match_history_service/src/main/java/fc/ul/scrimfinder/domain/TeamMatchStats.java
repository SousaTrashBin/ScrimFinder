package fc.ul.scrimfinder.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SoftDelete;

@Embeddable
@Getter
@Setter
@SoftDelete
public class TeamMatchStats {
    @Column(name = "team_kills", nullable = false)
    private Integer teamKills;

    @Column(name = "team_deaths", nullable = false)
    private Integer teamDeaths;

    @Column(name = "team_assists", nullable = false)
    private Integer teamAssists;

    @Column(name = "team_healing", nullable = false)
    private Integer teamHealing;
}
