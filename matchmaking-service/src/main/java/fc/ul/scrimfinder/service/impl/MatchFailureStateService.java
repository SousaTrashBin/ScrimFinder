package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.domain.Match;
import fc.ul.scrimfinder.repository.MatchRepository;
import fc.ul.scrimfinder.util.MatchState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class MatchFailureStateService {

    @Inject MatchRepository matchRepository;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void markResultReportingFailed(UUID matchId) {
        Match match = matchRepository.findById(matchId);
        if (match == null) {
            return;
        }
        match.setState(MatchState.RESULT_REPORTING_FAILED);
        match.setEndedAt(LocalDateTime.now());
        matchRepository.persist(match);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Optional<MatchState> getMatchState(UUID matchId) {
        Match match = matchRepository.findById(matchId);
        return Optional.ofNullable(match).map(Match::getState);
    }
}
