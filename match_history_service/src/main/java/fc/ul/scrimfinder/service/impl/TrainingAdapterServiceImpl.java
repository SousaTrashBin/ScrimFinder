package fc.ul.scrimfinder.service.impl;

import fc.ul.scrimfinder.grpc.ForwardMatchRequest;
import fc.ul.scrimfinder.grpc.ForwardMatchResponse;
import fc.ul.scrimfinder.grpc.TrainingService;
import fc.ul.scrimfinder.service.TrainingAdapterService;
import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TrainingAdapterServiceImpl implements TrainingAdapterService {
    @Inject
    @GrpcClient("training-service")
    TrainingService trainingService;

    @Inject Logger logger;

    @Override
    @Retry(maxRetries = 4)
    @Timeout(10000)
    @CircuitBreaker()
    public boolean sendMatchForAnalysis(String riotMatchId) {
        try {
            ForwardMatchRequest request =
                    ForwardMatchRequest.newBuilder().setMatchId(riotMatchId).build();
            ForwardMatchResponse response = trainingService.forwardMatch(request).await().indefinitely();

            if (!response.getSuccess()) {
                logger.error("Training service returned failure: " + response.getMessage());
            }

            return response.getSuccess();
        } catch (Exception x) {
            logger.error("Error communicating with training-service via gRPC: " + x.getMessage(), x);
            return false;
        }
    }
}
