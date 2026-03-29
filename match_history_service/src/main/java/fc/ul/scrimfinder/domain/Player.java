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
@Table(
        name = "player",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"name", "tag"})})
public class Player {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "puuid", unique = true, nullable = false, updatable = false)
    private String puuid;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "tag", nullable = false)
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
