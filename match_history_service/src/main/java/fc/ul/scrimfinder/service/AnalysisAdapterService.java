package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.MatchDTO;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;

public interface AnalysisAdapterService {
    @CircuitBreaker()
    boolean sendMatchForAnalysis(MatchDTO matchDTO);
}
