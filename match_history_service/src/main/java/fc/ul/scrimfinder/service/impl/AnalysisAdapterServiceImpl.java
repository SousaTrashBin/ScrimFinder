package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.client.AnalysisClient;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.service.AnalysisAdapterService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class AnalysisAdapterServiceImpl implements AnalysisAdapterService {
    @Inject @RestClient AnalysisClient analysisClient;

    @Override
    public boolean sendMatchForAnalysis(MatchDTO matchDTO) {
        // TODO
        return false;
    }
}
