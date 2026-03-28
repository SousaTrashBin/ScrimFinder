package fc.ul.scrimfinder.domain;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SoftDelete;

@Entity
@Getter
@Setter
@SoftDelete
@Table(name = "match")
public class Match {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "riot_match_id", nullable = false)
    private String riotMatchId;

    @Column(name = "queue_id", nullable = false)
    private Long queueId;

    @Column(name = "patch", nullable = false)
    private String patch;

    @Column(name = "game_creation", nullable = false)
    private Long gameCreation;

    @Column(name = "game_duration", nullable = false)
    private Long gameDuration;

    @OneToMany(
            mappedBy = "match",
            fetch = FetchType.EAGER,
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<PlayerMatchStats> playerMatchStats = new ArrayList<>();

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "side", column = @Column(name = "blue_team_side")),
        @AttributeOverride(name = "teamKills", column = @Column(name = "blue_team_kills")),
        @AttributeOverride(name = "teamDeaths", column = @Column(name = "blue_team_deaths")),
        @AttributeOverride(name = "teamAssists", column = @Column(name = "blue_team_assists")),
        @AttributeOverride(name = "teamHealing", column = @Column(name = "blue_team_healing")),
    })
    private TeamMatchStats blue;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "side", column = @Column(name = "red_team_side")),
        @AttributeOverride(name = "teamKills", column = @Column(name = "red_team_kills")),
        @AttributeOverride(name = "teamDeaths", column = @Column(name = "red_team_deaths")),
        @AttributeOverride(name = "teamAssists", column = @Column(name = "red_team_assists")),
        @AttributeOverride(name = "teamHealing", column = @Column(name = "red_team_healing")),
    })
    private TeamMatchStats red;

    public void addPlayerMatchStat(PlayerMatchStats playerMatchStat) {
        playerMatchStats.add(playerMatchStat);
        playerMatchStat.setMatch(this);
    }

    public void removePlayerMatchStat(PlayerMatchStats playerMatchStat) {
        playerMatchStats.remove(playerMatchStat);
        playerMatchStat.setMatch(null);
    }
}
