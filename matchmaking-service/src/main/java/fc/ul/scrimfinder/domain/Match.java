package fc.ul.scrimfinder.domain;

import fc.ul.scrimfinder.util.MatchState;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "match")
public class Match {

    @Id private UUID id = UUID.randomUUID();

    @OneToOne(optional = false)
    @JoinColumn(name = "lobby_id")
    private Lobby lobby;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchState state = MatchState.SEARCHING;

    @ElementCollection
    @CollectionTable(name = "match_acceptances", joinColumns = @JoinColumn(name = "match_id"))
    @Column(name = "player_id")
    private Set<UUID> acceptedPlayerIds = new HashSet<>();

    private String externalGameId;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
