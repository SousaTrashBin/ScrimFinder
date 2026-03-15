package fc.ul.scrimfinder.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.common.constraint.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.QueryParam;

public record Rank(
        @QueryParam("tier")
        @NotNull
        Tier tier,

        @QueryParam("division")
        @Min(value = 1, message = "The division must be between 1 and 4")
        @Max(value = 4, message = "The division must be between 1 and 4")
        Integer division,

        @QueryParam("lps")
        @Min(value = 0, message = "The league points must be greater than or equal to 0")
        Integer lps
) {
    public static Rank valueOf(String value) {
        if (value == null || value.isBlank()) return null;

        ObjectMapper mapper = new ObjectMapper();
        JsonNode rankNode;
        try {
            rankNode = mapper.readTree(value);
        } catch (Exception x) {
            throw new IllegalArgumentException("Invalid rank object format: " + value);
        }

        JsonNode tierNode = rankNode.findValue("tier");
        if (tierNode == null) {
            throw new IllegalArgumentException("Rank tier is required: " + value);
        }
        Tier tier =  Tier.fromTierName(tierNode.asText());
        if (tier == null) {
            throw new IllegalArgumentException("Invalid tier name: " + value);
        }

        JsonNode divisionNode = rankNode.findValue("division");
        if (divisionNode == null) {
            throw new IllegalArgumentException("Rank division is required: " + value);
        }
        Integer division = divisionNode.asInt();

        JsonNode lpsNode = rankNode.findValue("lps");
        if (lpsNode == null) {
            throw new IllegalArgumentException("Rank league points are required: " + value);
        }
        Integer lps = lpsNode.asInt();

        return new Rank(tier, division, lps);
    }
}
