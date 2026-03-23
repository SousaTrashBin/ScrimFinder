package fc.ul.scrimfinder.domain;

import fc.ul.scrimfinder.util.Champion;
import fc.ul.scrimfinder.util.Role;
import fc.ul.scrimfinder.util.TeamSide;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SoftDelete;

@Entity
@Getter
@Setter
@SoftDelete
@Table(name = "player_match_stats")
public class PlayerMatchStats {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "match_id", referencedColumnName = "id", nullable = false)
    private Match match;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "player_id", referencedColumnName = "id", nullable = false)
    private Player player;

    @Column(name = "kills", nullable = false)
    private Integer kills;

    @Column(name = "deaths", nullable = false)
    private Integer deaths;

    @Column(name = "assists", nullable = false)
    private Integer assists;

    @Column(name = "healing", nullable = false)
    private Integer healing;

    @Column(name = "damage_to_players", nullable = false)
    private Integer damageToPlayers;

    @Column(name = "wards", nullable = false)
    private Integer wards;

    @Column(name = "gold", nullable = false)
    private Integer gold;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "role", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "champion", nullable = false)
    private Champion champion;

    @Column(name = "cs_per_minute", nullable = false)
    private Double csPerMinute;

    @Column(name = "killed_minions", nullable = false)
    private Integer killedMinions;

    @Column(name = "triple_kills", nullable = false)
    private Integer tripleKills;

    @Column(name = "quad_kills", nullable = false)
    private Integer quadKills;

    @Column(name = "penta_kills", nullable = false)
    private Integer pentaKills;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "side", nullable = false)
    private TeamSide teamSide;

    @Column(name = "won", nullable = false)
    private Boolean won;

    @Column(name = "mmr_delta", nullable = true)
    private Integer mmrDelta;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerMatchStats)) return false;
        return id != null && id.equals(((PlayerMatchStats) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
