package fc.ul.scrimfinder.domain;

import fc.ul.scrimfinder.util.MatchState;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "match")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "lobby_id")
    private Lobby lobby;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchState state = MatchState.SEARCHING;

    @ElementCollection
    @CollectionTable(name = "match_acceptances", joinColumns = @JoinColumn(name = "match_id"))
    @Column(name = "player_id")
    private Set<Long> acceptedPlayerIds = new HashSet<>();

    private String externalGameId;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
