package fc.ul.scrimfinder.dto.response;

import java.util.List;
import java.util.UUID;

public record PlayerDTO(
        UUID id,
        String discordUsername,
        List<RiotAccountDTO> riotAccounts,
        Integer soloqMMR,
        Integer flexMMR) {}
