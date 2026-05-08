package fc.ul.scrimfinder.domain;

import fc.ul.scrimfinder.util.Region;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "lobby")
public class Lobby {

    @Id private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false)
    @JoinColumn(name = "queue_id")
    private Queue queue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Region region;

    @OneToMany(mappedBy = "lobby", cascade = CascadeType.ALL)
    private List<MatchTicket> tickets = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
