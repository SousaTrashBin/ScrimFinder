package fc.ul.scrimfinder.controller;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import fc.ul.scrimfinder.dto.response.QueueDTO;
import fc.ul.scrimfinder.service.QueueService;
import fc.ul.scrimfinder.util.MMRRuleType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class QueueResourceTest {

    @InjectMock QueueService queueService;

    @Test
    void testGetQueueEndpoint() {
        UUID queueId = UUID.randomUUID();
        QueueDTO queue = new QueueDTO(queueId, "SoloQ", MMRRuleType.SOLOQ_RANK, 1000, true);

        when(queueService.getQueue(eq(queueId))).thenReturn(queue);

        given()
                .when()
                .get("/queue/" + queueId)
                .then()
                .statusCode(200)
                .body("id", is(queueId.toString()))
                .body("name", is("SoloQ"));
    }
}
