package fc.ul.scrimfinder.dto.response;

import java.util.UUID;
import lombok.Data;

@Data
public class PlayerDTO {
    private UUID id;
    private String username;
}
