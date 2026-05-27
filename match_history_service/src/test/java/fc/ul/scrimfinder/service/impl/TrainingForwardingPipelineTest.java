package fc.ul.scrimfinder.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fc.ul.scrimfinder.domain.TrainingSyncOutboxEvent;
import fc.ul.scrimfinder.dto.request.PlayerStatsDTO;
import fc.ul.scrimfinder.dto.request.TeamStatsDTO;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.repository.TrainingSyncOutboxRepository;
import fc.ul.scrimfinder.service.DetailFillingAdapterService;
import fc.ul.scrimfinder.service.MatchHistoryService;
import fc.ul.scrimfinder.service.TrainingAdapterService;
import fc.ul.scrimfinder.util.Champion;
import fc.ul.scrimfinder.util.RiotId;
import fc.ul.scrimfinder.util.Role;
import fc.ul.scrimfinder.util.TeamSide;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TrainingForwardingPipelineTest {

    @InjectMock TrainingAdapterService trainingAdapterService;
    @InjectMock DetailFillingAdapterService detailFillingAdapterService;

    @Inject MatchHistoryService matchHistoryService;
    @Inject TrainingSyncOutboxService trainingSyncOutboxService;
    @Inject TrainingSyncOutboxRepository outboxRepository;
    @Inject EntityManager entityManager;

    @Test
    void savingMatchMarksTrainingOutboxSentWhenForwardingSucceeds() {
        String matchId = "EUW1_PIPE_SENT";
        when(detailFillingAdapterService.getMatch(eq(matchId))).thenReturn(match(matchId));
        when(trainingAdapterService.sendMatchForAnalysis(eq(matchId))).thenReturn(true);

        matchHistoryService.addMatchById(matchId, queueId(), Map.of("P_PIPE", 12));

        TrainingSyncOutboxEvent event = outboxEvent(matchId);
        assertEquals("SENT", event.getStatus());
        assertEquals(1, event.getAttempts());
        assertNull(event.getLastError());
        assertNull(event.getNextAttemptAt());
        verify(trainingAdapterService, times(1)).sendMatchForAnalysis(eq(matchId));
    }

    @Test
    void savingMatchLeavesPendingOutboxAndRetryCanDeliverWhenForwardingFails() {
        String matchId = "EUW1_PIPE_RETRY";
        when(detailFillingAdapterService.getMatch(eq(matchId))).thenReturn(match(matchId));
        when(trainingAdapterService.sendMatchForAnalysis(eq(matchId))).thenReturn(false, true);

        matchHistoryService.addMatchById(matchId, queueId(), Map.of("P_PIPE", 12));

        TrainingSyncOutboxEvent pending = outboxEvent(matchId);
        assertEquals("PENDING", pending.getStatus());
        assertEquals(1, pending.getAttempts());
        assertNotNull(pending.getLastError());
        assertNotNull(pending.getNextAttemptAt());

        boolean delivered = trainingSyncOutboxService.processSinglePending(matchId);

        TrainingSyncOutboxEvent sent = outboxEvent(matchId);
        assertEquals(true, delivered);
        assertEquals("SENT", sent.getStatus());
        assertEquals(2, sent.getAttempts());
        assertNull(sent.getLastError());
        assertNull(sent.getNextAttemptAt());
        verify(trainingAdapterService, times(2)).sendMatchForAnalysis(eq(matchId));
    }

    private TrainingSyncOutboxEvent outboxEvent(String matchId) {
        entityManager.clear();
        return outboxRepository.find("riotMatchId", matchId).firstResult();
    }

    private static UUID queueId() {
        return UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    }

    private static MatchDTO match(String matchId) {
        return new MatchDTO(
                matchId,
                null,
                "14.10",
                1_773_524_078_235L,
                1_800L,
                List.of(
                        new PlayerStatsDTO(
                                new RiotId("P_PIPE", "Pipeline", "EUW", 1, 30),
                                8,
                                2,
                                9,
                                100,
                                21_000,
                                12,
                                14_000,
                                Role.BOTTOM,
                                Champion.ASHE,
                                8.0,
                                240,
                                0,
                                0,
                                0,
                                TeamSide.BLUE,
                                true,
                                null)),
                List.of(
                        new TeamStatsDTO(TeamSide.BLUE, 20, 10, 35, 500),
                        new TeamStatsDTO(TeamSide.RED, 10, 20, 15, 300)));
    }
}
