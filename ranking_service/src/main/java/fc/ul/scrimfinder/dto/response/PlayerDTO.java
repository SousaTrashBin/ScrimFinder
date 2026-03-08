package fc.ul.scrimfinder.dto.response;

public record PlayerDTO(
        Long id,
        String discordUsername,
        String lolAccountPPUID,
        Integer soloqMMR,
        Integer flexMMR
) {
}
