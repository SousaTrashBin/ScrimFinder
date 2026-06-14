package fc.ul.scrimfinder.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class TrainingGrpcContractTest {

    @Test
    void generatedTrainingGrpcContractMatchesPythonTrainingService() {
        var descriptor = TrainingProto.getDescriptor();

        assertEquals(
                "scrimfinder.TrainingService",
                descriptor.findServiceByName("TrainingService").getFullName());
        assertNotNull(
                descriptor.findMessageTypeByName("ForwardMatchRequest").findFieldByName("match_id"));
        assertNotNull(
                descriptor.findMessageTypeByName("ForwardMatchRequest").findFieldByName("source"));
        assertNotNull(
                descriptor.findMessageTypeByName("ForwardMatchResponse").findFieldByName("game_id"));
        assertNotNull(
                descriptor.findMessageTypeByName("ForwardMatchResponse").findFieldByName("draft_ok"));
        assertNotNull(
                descriptor.findMessageTypeByName("ForwardMatchResponse").findFieldByName("build_ok"));
        assertNotNull(
                descriptor.findMessageTypeByName("ForwardMatchResponse").findFieldByName("perf_ok"));
    }
}
