package fc.ul.scrimfinder.util;

import jakarta.enterprise.inject.Vetoed;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Vetoed
public class RiotId {
    private String playerName;
    private String playerTag;
    private Integer playerIcon;
}
