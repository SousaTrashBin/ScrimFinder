package fc.ul.scrimfinder.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "player")
public class Player {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "discord_username", nullable = false, unique = true)
    private String discordUsername;
}
