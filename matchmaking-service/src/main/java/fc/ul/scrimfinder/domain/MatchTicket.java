package fc.ul.scrimfinder.domain;

import fc.ul.scrimfinder.util.Region;
import fc.ul.scrimfinder.util.Role;
import fc.ul.scrimfinder.util.TicketStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "match_ticket")
public class MatchTicket {

    @Id private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false)
    @JoinColumn(name = "player_id")
    private Player player;

    @ManyToOne(optional = false)
    @JoinColumn(name = "queue_id")
    private Queue queue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Region region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.NONE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.IN_QUEUE;

    @Column(nullable = false)
    private int mmr;

    @Column(name = "riot_puuid")
    private String riotPuuid;

    @Column private Integer team;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "lobby_id")
    private Lobby lobby;
}
