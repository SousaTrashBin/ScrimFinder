package fc.ul.scrimfinder.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "player")
public class Player {

    @Id private Long id;

    @Column(nullable = false, unique = true)
    private String username;
}
