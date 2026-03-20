package fc.ul.scrimfinder.dto.response;

import java.util.List;

public record PlayerDTO(
        Long id,
        String discordUsername,
        List<RiotAccountDTO> riotAccounts,
        Integer soloqMMR,
        Integer flexMMR) {}
