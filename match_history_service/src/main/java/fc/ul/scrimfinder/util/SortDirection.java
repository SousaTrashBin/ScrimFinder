package fc.ul.scrimfinder.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.QueryParam;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum SortDirection {
    @JsonProperty(value = "asc")
    ASC(""),

    @JsonProperty(value = "desc")
    DESC("-");

    final String direction;
}
