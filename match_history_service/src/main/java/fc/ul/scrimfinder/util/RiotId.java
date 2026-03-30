package fc.ul.scrimfinder.util;

import jakarta.enterprise.inject.Vetoed;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Data
@Vetoed
public class RiotId {
    private String puuid;
    private String playerName;
    private String playerTag;
    private Integer summonerIcon;
    private Integer summonerLevel;
}
