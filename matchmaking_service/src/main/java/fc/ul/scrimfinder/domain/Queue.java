package fc.ul.scrimfinder.domain;

import fc.ul.scrimfinder.util.MatchmakingMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "queue")
public class Queue {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column
    private String namespace;

    @Column(nullable = false)
    private int requiredPlayers = 10;

    @Column(nullable = false)
    private boolean isRoleQueue = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchmakingMode mode = MatchmakingMode.NORMAL;

    @Column(nullable = false)
    private int mmrWindow = 200;
}
