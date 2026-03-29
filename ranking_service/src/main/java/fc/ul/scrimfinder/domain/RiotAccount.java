package fc.ul.scrimfinder.domain;

import fc.ul.scrimfinder.util.Region;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "riot_account")
@Getter
@Setter
public class RiotAccount {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    private String puuid;

    @Column(nullable = false)
    private String gameName;

    @Column(nullable = false)
    private String tagLine;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Region region;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary = false;
}
