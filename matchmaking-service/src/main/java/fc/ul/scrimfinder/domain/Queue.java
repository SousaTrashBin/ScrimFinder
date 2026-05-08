package fc.ul.scrimfinder.domain;

import fc.ul.scrimfinder.util.MatchmakingMode;
import fc.ul.scrimfinder.util.Region;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "queue")
public class Queue {

    @Id private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private Region region;

    @Column private String namespace;

    @Column(name = "required_players", nullable = false)
    private int requiredPlayers = 10;

    @Column(name = "is_role_queue", nullable = false)
    private boolean isRoleQueue = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchmakingMode mode = MatchmakingMode.NORMAL;

    @Column(name = "mmr_window", nullable = false)
    private int mmrWindow = 200;
}
