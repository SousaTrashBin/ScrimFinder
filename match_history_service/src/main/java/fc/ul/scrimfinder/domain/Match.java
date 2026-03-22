package fc.ul.scrimfinder.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SoftDelete;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@SoftDelete
@Table(name = "match")
public class Match {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private Long id;

    @Column(name = "riot_match_id", unique = true, nullable = false)
    private String riotMatchId;

    @Column(name = "queue_id", nullable = false)
    private Long queueId;

    @Column(name = "patch", nullable = false)
    private String patch;

    @Column(name = "game_creation", nullable = false)
    private LocalDateTime gameCreation;

    @Column(name = "game_duration", nullable = false)
    private Long gameDuration;

    @OneToMany(mappedBy = "match", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private List<PlayerMatchStats> playerMatchStats;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "teamKills", column = @Column(name = "blue_team_kills")),
            @AttributeOverride(name = "teamDeaths", column = @Column(name = "blue_team_deaths")),
            @AttributeOverride(name = "teamAssists", column = @Column(name = "blue_team_assists")),
            @AttributeOverride(name = "teamHealing", column = @Column(name = "blue_team_healing")),
    })
    private TeamMatchStats blue;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "teamKills", column = @Column(name = "red_team_kills")),
            @AttributeOverride(name = "teamDeaths", column = @Column(name = "red_team_deaths")),
            @AttributeOverride(name = "teamAssists", column = @Column(name = "red_team_assists")),
            @AttributeOverride(name = "teamHealing", column = @Column(name = "red_team_healing")),
    })
    private TeamMatchStats red;
}
