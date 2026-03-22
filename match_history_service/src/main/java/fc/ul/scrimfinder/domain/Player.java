package fc.ul.scrimfinder.domain;

import jakarta.persistence.*;
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

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "tag", nullable = false)
    private String tag;

    @OneToMany(mappedBy = "player", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<PlayerMatchStats> playerMatchStats;
}
