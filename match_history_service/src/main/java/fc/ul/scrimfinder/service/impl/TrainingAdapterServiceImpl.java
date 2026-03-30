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

@ApplicationScoped
public class TrainingAdapterServiceImpl implements TrainingAdapterService {
    @Inject
    @GrpcClient("training-service")
    TrainingService trainingService;

    @Override
    @Retry(maxRetries = 4)
    @Timeout(2000)
    @CircuitBreaker()
    public boolean sendMatchForAnalysis(String riotMatchId) {
        try {
            ForwardMatchResponse response =
                    trainingService
                            .forwardMatch(ForwardMatchRequest.newBuilder().setMatchId(riotMatchId).build())
                            .await()
                            .indefinitely();
            return response.getSuccess();
        } catch (Exception x) {
            return false;
        }
    }
}
