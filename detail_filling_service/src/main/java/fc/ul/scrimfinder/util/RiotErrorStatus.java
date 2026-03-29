package fc.ul.scrimfinder.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiotErrorStatus {
    public Integer status_code;
    public String message;
}
