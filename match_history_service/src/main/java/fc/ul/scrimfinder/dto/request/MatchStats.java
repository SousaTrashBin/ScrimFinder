package fc.ul.scrimfinder.dto.request;

import com.arjuna.common.internal.util.propertyservice.ConcatenationPrefix;
import com.arjuna.common.internal.util.propertyservice.PropertyPrefix;
import fc.ul.scrimfinder.util.PatchInterval;
import fc.ul.scrimfinder.util.TimeInterval;
import io.quarkus.arc.runtime.AdditionalBean;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import lombok.experimental.FieldNameConstants;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestQuery;

import java.beans.BeanProperty;
import java.util.List;

public record MatchStats(
        @QueryParam("ranks") List<String> ranks,
        @QueryParam("champions") List<String> champions,
        @QueryParam("matchTripleKills") Integer matchTripleKills,
        @QueryParam("matchQuadKills") Integer matchQuadKills,
        @QueryParam("matchPentaKills") Integer matchPentaKills,
        @BeanParam PatchInterval patchInterval,
        @BeanParam TimeInterval timeInterval,
        @QueryParam("teams") List<TeamStats> teams,
        @QueryParam("queueId") Long queueId,
        @QueryParam("players") List<PlayerStats> players
) {
}
