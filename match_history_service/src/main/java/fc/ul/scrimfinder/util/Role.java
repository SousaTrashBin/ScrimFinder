package fc.ul.scrimfinder.util;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Role {
    @JsonProperty(value = "top") TOP,
    @JsonProperty(value = "jungle") JUNGLE,
    @JsonProperty(value = "mid") MID,
    @JsonProperty(value = "bottom") BOTTOM,
    @JsonProperty(value = "support") SUPPORT
}
