package fc.ul.scrimfinder;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "config")
public interface Config {
    String riotApiKey();
}
