package fc.ul.scrimfinder.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "player")
@Getter
@Setter
public class Player {

    @Id
    @Column(nullable = false, updatable = false)
    private Long id;

    @Column(nullable = false, unique = true)
    private String discordUsername;

    @Column(name = "lol_account_puuid", unique = true)
    private String lolAccountPPUID;

    @Column(nullable = false)
    private Integer soloqMMR = 1000;

    @Column(nullable = false)
    private Integer flexMMR = 1000;

}
