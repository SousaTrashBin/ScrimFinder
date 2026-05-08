package fc.ul.scrimfinder.domain;

import fc.ul.scrimfinder.util.MMRRuleType;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "queue")
public class QueueEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "mmr_rule_type", nullable = false)
    private MMRRuleType mmrRuleType;

    @Column(name = "initial_mmr", nullable = false)
    private int initialMMR = 1000;

    @Column(nullable = false)
    private boolean active = true;
}
