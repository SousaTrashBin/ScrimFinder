package fc.ul.scrimfinder.service;

import fc.ul.scrimfinder.dto.response.MatchDTO;

public interface AnalysisAdapterService {
    boolean sendMatchForAnalysis(MatchDTO matchDTO);
}
