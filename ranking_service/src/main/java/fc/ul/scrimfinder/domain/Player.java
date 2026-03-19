package fc.ul.scrimfinder.domain;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
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

    @OneToMany(mappedBy = "player", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RiotAccount> riotAccounts = new ArrayList<>();

    @Column(nullable = false)
    private Integer soloqMMR = 1000;

    @Column(nullable = false)
    private Integer flexMMR = 1000;

    public RiotAccount getPrimaryAccount() {
        return riotAccounts.stream().filter(RiotAccount::isPrimary).findFirst().orElse(null);
    }
}
