package fc.ul.scrimfinder.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
public class QueueEntity {
    @Id
    @Column(nullable = false)
    private Long id;

}
