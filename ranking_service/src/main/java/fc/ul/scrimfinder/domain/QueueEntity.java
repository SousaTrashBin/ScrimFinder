package fc.ul.scrimfinder.domain;

import fc.ul.scrimfinder.util.MMRRuleType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "queue")
public class QueueEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MMRRuleType mmrRuleType;

    @Column(nullable = false)
    private int initialMMR = 1000;

    @Column(nullable = false)
    private boolean active = true;
}
