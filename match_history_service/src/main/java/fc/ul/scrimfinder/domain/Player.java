package fc.ul.scrimfinder.domain;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SoftDelete;

@Entity
@Getter
@Setter
@SoftDelete
@Table(
        name = "player",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"name", "tag"})})
public class Player {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private Long id;

    @Column(name = "puuid", unique = true, nullable = false, updatable = false, length = 78)
    private String puuid;

    @Column(name = "name", nullable = false, length = 16)
    private String name;

    @Column(name = "tag", nullable = false, length = 5)
    private String tag;

    @OneToMany(
            mappedBy = "player",
            fetch = FetchType.LAZY,
            cascade = CascadeType.REMOVE,
            orphanRemoval = true)
    private List<PlayerMatchStats> playerMatchStats = new ArrayList<>();

    public void addPlayerMatchStat(PlayerMatchStats playerMatchStat) {
        playerMatchStats.add(playerMatchStat);
        playerMatchStat.setPlayer(this);
    }

    public void removePlayerMatchStat(PlayerMatchStats playerMatchStat) {
        playerMatchStats.remove(playerMatchStat);
        playerMatchStat.setPlayer(null);
    }
}
