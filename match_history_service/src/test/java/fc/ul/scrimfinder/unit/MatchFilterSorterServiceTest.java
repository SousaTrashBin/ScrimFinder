package fc.ul.scrimfinder.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fc.ul.scrimfinder.dto.request.PlayerStatsDTO;
import fc.ul.scrimfinder.dto.request.TeamStatsDTO;
import fc.ul.scrimfinder.dto.request.filtering.MatchFilters;
import fc.ul.scrimfinder.dto.request.filtering.PlayerFilters;
import fc.ul.scrimfinder.dto.request.filtering.TeamFilters;
import fc.ul.scrimfinder.dto.request.sorting.SortParams;
import fc.ul.scrimfinder.dto.response.MatchDTO;
import fc.ul.scrimfinder.dto.response.PaginatedResponseDTO;
import fc.ul.scrimfinder.exception.InvalidIntervalException;
import fc.ul.scrimfinder.exception.InvalidPaginationParametersException;
import fc.ul.scrimfinder.util.*;
import fc.ul.scrimfinder.util.interval.DoubleInterval;
import fc.ul.scrimfinder.util.interval.IntegerInterval;
import fc.ul.scrimfinder.util.interval.LongInterval;
import fc.ul.scrimfinder.util.interval.PatchInterval;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;

@QuarkusTest
@Tag("unit")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MatchFilterSorterServiceTest {

    @Inject MatchFilterSorterService matchFilterSorterService;

    @Inject MatchHistoryService matchHistoryService;

    @InjectMock DetailFillingAdapterService detailFillingAdapterService;

    @InjectMock TrainingAdapterService trainingAdapterService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String MATCH_ID = "EUW1_7779779801";
    private static final String MATCH_ID2 = "EUW1_7798917216";
    private static final String MATCH_ID3 = "EUW1_7779779800";

    private static final String QUEUE_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String QUEUE_ID2 = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    private static Map<String, Integer> mmrDeltas;
    private static Map<String, Integer> mmrDeltas2;

    private static MatchDTO matchDTO;
    private static MatchDTO matchDTO2;
    private static MatchDTO matchDTO3;

    @BeforeAll
    public static void init() {
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        matchDTO =
                new MatchDTO(
                        MATCH_ID,
                        UUID.fromString(QUEUE_ID),
                        "16.5",
                        1773524078235L,
                        1362L,
                        List.of(
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "h_gAfdHVoI8vBJaIyyGOUqDJlXUGGePLUJdMpR1zoRVPdigrRKvMchFBAzIlOUpd4BLlr6Ba8BRSjw",
                                                "Maynter",
                                                "EUW",
                                                1301,
                                                787),
                                        1,
                                        5,
                                        1,
                                        3776,
                                        64721,
                                        7,
                                        6451,
                                        Role.TOP,
                                        Champion.GNAR,
                                        6.1,
                                        139,
                                        0,
                                        0,
                                        0,
                                        TeamSide.BLUE,
                                        false,
                                        1),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "OR5LGqOXhHVe8xj0IWrZHJWvOqLMVprQYTKbhawJQjzIFjDaFwCmnLc8Eid0rWTsZ6DRVCz65lsjrw",
                                                "Fentasm",
                                                "FENT",
                                                6278,
                                                656),
                                        1,
                                        7,
                                        4,
                                        5874,
                                        142137,
                                        4,
                                        8100,
                                        Role.JUNGLE,
                                        Champion.VAYNE,
                                        1.5,
                                        33,
                                        0,
                                        0,
                                        0,
                                        TeamSide.BLUE,
                                        false,
                                        2),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "aqzT2pS2aQuBrkKv4izgg99_X73edM43rJoSbOhEhWg1lThgMB4yDcQsU8hGtXVTZnQmAYOawLMxeA",
                                                "SlayderW",
                                                "EUWW",
                                                28,
                                                103),
                                        4,
                                        5,
                                        2,
                                        759,
                                        122557,
                                        5,
                                        9823,
                                        Role.MID,
                                        Champion.ORIANNA,
                                        9.3,
                                        211,
                                        0,
                                        0,
                                        0,
                                        TeamSide.BLUE,
                                        false,
                                        3),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "neqSH5XmjVkKkOki4MDq5Re0WwHMJ8BKsh_URZjThuWOINOoYvoe-YNU8Ll48ojGN5oCn4owcd2UQg",
                                                "KISS OF DEATH",
                                                "YEN",
                                                1455,
                                                476),
                                        6,
                                        9,
                                        4,
                                        1315,
                                        69636,
                                        7,
                                        9597,
                                        Role.BOTTOM,
                                        Champion.ASHE,
                                        6.4,
                                        146,
                                        0,
                                        0,
                                        0,
                                        TeamSide.BLUE,
                                        false,
                                        4),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "5GC_pKq053P0rngh7uTcjwB79V4vs1JoA3wzcUYgqlRr6tB-iNEF51CJnOQKrk4daGa5g8AdNezprw",
                                                "Last dance",
                                                "Hexom",
                                                1637,
                                                106),
                                        1,
                                        9,
                                        8,
                                        4690,
                                        23399,
                                        24,
                                        6350,
                                        Role.SUPPORT,
                                        Champion.SERAPHINE,
                                        1.3,
                                        29,
                                        0,
                                        0,
                                        0,
                                        TeamSide.BLUE,
                                        false,
                                        5),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "Yhpm3VDVVQ09nAF9qUANOLFyItHikNj54vxB1yMzHmqIEohkIKEAeX-al94bBnYWtmsYv5CN6W3BvQ",
                                                "baby kiin",
                                                "keem",
                                                6,
                                                120),
                                        5,
                                        3,
                                        5,
                                        2833,
                                        144290,
                                        10,
                                        11484,
                                        Role.TOP,
                                        Champion.JAYCE,
                                        8.4,
                                        191,
                                        0,
                                        0,
                                        0,
                                        TeamSide.RED,
                                        true,
                                        -1),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "avrrbs2xmpx-CjjNFWyRENlXRmLPTN4X-igl6r-I732hIPmTV_e5NlJ3W4qVJ58inDq4hY_6e5sqOA",
                                                "J1HUIV",
                                                "000",
                                                29,
                                                445),
                                        11,
                                        1,
                                        6,
                                        9874,
                                        295508,
                                        4,
                                        13424,
                                        Role.JUNGLE,
                                        Champion.QIYANA,
                                        1.7,
                                        39,
                                        1,
                                        0,
                                        0,
                                        TeamSide.RED,
                                        true,
                                        -2),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "CFcq57-yO1Shwn5cVI0nkIHWIGgzl-QV1g5qMAliHHZuFo_HBoX-S6ZjhidAKLenTr5ziJAHXxw-aQ",
                                                "Yoriichı",
                                                "EUW",
                                                5045,
                                                210),
                                        5,
                                        1,
                                        5,
                                        6144,
                                        140039,
                                        5,
                                        10871,
                                        Role.MID,
                                        Champion.AHRI,
                                        9.7,
                                        221,
                                        0,
                                        0,
                                        0,
                                        TeamSide.RED,
                                        true,
                                        -3),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "vyzwo-B74dAgHNsstNoWPioVU8bLh6kye-0xtdFTMQwM4Y-kCSZfwMpf29CadJJ1AvpYEPV5yNMicA",
                                                "KC NEXT ADKING",
                                                "EUW",
                                                29,
                                                727),
                                        13,
                                        3,
                                        12,
                                        12959,
                                        110972,
                                        10,
                                        13924,
                                        Role.BOTTOM,
                                        Champion.SENNA,
                                        7.3,
                                        165,
                                        0,
                                        0,
                                        0,
                                        TeamSide.RED,
                                        true,
                                        -4),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "fwP-3K-Z_ghLAruQYlntd03YSdRCJeD7kaJQFujGJJpiwZ-ie9Rwkc9SmsiExqcnbnssXGmoAtyqHA",
                                                "berebooo",
                                                "TR1",
                                                3158,
                                                346),
                                        1,
                                        6,
                                        26,
                                        3544,
                                        15953,
                                        33,
                                        8253,
                                        Role.SUPPORT,
                                        Champion.BLITZCRANK,
                                        0.7,
                                        15,
                                        0,
                                        0,
                                        0,
                                        TeamSide.RED,
                                        true,
                                        -5)),
                        List.of(
                                new TeamStatsDTO(TeamSide.BLUE, 13, 35, 19, 16414),
                                new TeamStatsDTO(TeamSide.RED, 35, 14, 54, 35354)));

        matchDTO2 =
                new MatchDTO(
                        MATCH_ID2,
                        UUID.fromString(QUEUE_ID2),
                        "16.6",
                        1774482572023L,
                        1386L,
                        List.of(
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "18qWu0pwwA_sfCE8Ih_jIJhJiC7TlRK1dHXsKCZ_mFIAYPMf5-YQKH7gUFlDE9Fh-YZtYDsRMmySYg",
                                                "Test40",
                                                "4010",
                                                29,
                                                94),
                                        0,
                                        9,
                                        8,
                                        3370,
                                        86301,
                                        1,
                                        7050,
                                        Role.TOP,
                                        Champion.SINGED,
                                        6.2,
                                        144,
                                        0,
                                        0,
                                        0,
                                        TeamSide.BLUE,
                                        true,
                                        1),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "M6xuDdkAxNyajMiFto9VTsdP_JTatZlz4xZ06sd-UzTHWuzcyKE23C0bR6zSKSmvR0mfvuciU-NFZQ",
                                                "Odysseus",
                                                "131",
                                                26,
                                                1473),
                                        6,
                                        2,
                                        9,
                                        5796,
                                        266089,
                                        4,
                                        11505,
                                        Role.JUNGLE,
                                        Champion.TALIYAH,
                                        1.1,
                                        25,
                                        0,
                                        0,
                                        0,
                                        TeamSide.BLUE,
                                        true,
                                        2),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "oM5fTuy-bSHFjW_CJJ5_nYolZ1WTFge_0_caldn2ylpUiJV6glA9RfmNDE35xHyakwejILdYMfBUOg",
                                                "SeRiN1",
                                                "EUW",
                                                1630,
                                                1613),
                                        12,
                                        4,
                                        9,
                                        4117,
                                        148942,
                                        5,
                                        13066,
                                        Role.MID,
                                        Champion.ZED,
                                        8.9,
                                        206,
                                        0,
                                        0,
                                        0,
                                        TeamSide.BLUE,
                                        true,
                                        3),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "g9evN71NQIXfaQYH5ZdYMwZbEeg1wni_uiyQh__G0iD3bnz7yjHVA-Xi341gdhNpQ25hd58N1dth4w",
                                                "flying high",
                                                "exo",
                                                29,
                                                65),
                                        12,
                                        3,
                                        7,
                                        5760,
                                        140461,
                                        10,
                                        13929,
                                        Role.BOTTOM,
                                        Champion.ZERI,
                                        8.0,
                                        184,
                                        0,
                                        0,
                                        0,
                                        TeamSide.BLUE,
                                        true,
                                        4),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "yzV2y2SXD9LYlAiiJ8Rp3ehgDw5vSYHg-g1ai7ftYw4Ap2_Rz6fuKyyC-hh6ELcj-PwFueE4irOLDw",
                                                "femcel goonette",
                                                "khhv",
                                                4533,
                                                99),
                                        2,
                                        3,
                                        18,
                                        5789,
                                        15423,
                                        28,
                                        7722,
                                        Role.SUPPORT,
                                        Champion.LULU,
                                        0.8,
                                        18,
                                        0,
                                        0,
                                        0,
                                        TeamSide.BLUE,
                                        true,
                                        5),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "lCNhDWC2_gIx4v9nV8VsaBkLnUzwTCERUlP4WwQ5IwIE2gbv1MHkXkfjNs3Nm2tKXmmbM_I9TaNhIA",
                                                "bedoes2115",
                                                "SMOKI",
                                                1114,
                                                184),
                                        4,
                                        2,
                                        6,
                                        6759,
                                        155694,
                                        5,
                                        10907,
                                        Role.TOP,
                                        Champion.GNAR,
                                        9.6,
                                        222,
                                        0,
                                        0,
                                        0,
                                        TeamSide.RED,
                                        false,
                                        -1),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "fyBU5HDdwtPD0bnPxU0Do7aDxT1voRiqyJH9m4m10R9E9Gkhx88TcY0AuWFNqLfefkw2xmQAIlpZXw",
                                                "kappachungus",
                                                "won",
                                                29,
                                                900),
                                        0,
                                        7,
                                        11,
                                        6480,
                                        134579,
                                        2,
                                        7386,
                                        Role.JUNGLE,
                                        Champion.JARVAN_IV,
                                        0.3,
                                        6,
                                        0,
                                        0,
                                        0,
                                        TeamSide.RED,
                                        false,
                                        -2),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "EIOzLS5LzdIUswGZu39ERMRucu0NOz4OzM3g4P1tkfxPA8jUHeO31DbkwOLddLzQABdwZ0Lx7qC9LQ",
                                                "Gonzo",
                                                "2843",
                                                1630,
                                                829),
                                        9,
                                        8,
                                        0,
                                        1771,
                                        98682,
                                        9,
                                        9748,
                                        Role.MID,
                                        Champion.QUINN,
                                        6.5,
                                        151,
                                        0,
                                        0,
                                        0,
                                        TeamSide.RED,
                                        false,
                                        -3),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "wg70K5vl52HTn_wvM15Ww62K_-xY9iHaV4L4Ua-_yRx-ApUosLJQdYrzIjHNCaz5O4UbIe-6griPwQ",
                                                "ANDA",
                                                "RIEL",
                                                7084,
                                                817),
                                        2,
                                        8,
                                        6,
                                        2135,
                                        89009,
                                        9,
                                        9575,
                                        Role.BOTTOM,
                                        Champion.ASHE,
                                        8.0,
                                        184,
                                        0,
                                        0,
                                        0,
                                        TeamSide.RED,
                                        false,
                                        -4),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "k_-JlBGNZWdw8j9DFKrEJgdqF8DTrpAZxqyLnbObjq__klQIyvCNvdxl8wvJ2I5uvYnkfKSXnnOlEQ",
                                                "MISA 113",
                                                "Kurii",
                                                22,
                                                776),
                                        4,
                                        7,
                                        6,
                                        8325,
                                        29899,
                                        23,
                                        7688,
                                        Role.SUPPORT,
                                        Champion.SERAPHINE,
                                        1.6,
                                        38,
                                        0,
                                        0,
                                        0,
                                        TeamSide.RED,
                                        false,
                                        -5)),
                        List.of(
                                new TeamStatsDTO(TeamSide.BLUE, 32, 21, 51, 24832),
                                new TeamStatsDTO(TeamSide.RED, 19, 32, 29, 25470)));

        // Only differs from matchDTO on the riotMatchId and blue team kills (it has less)
        matchDTO3 =
                new MatchDTO(
                        MATCH_ID3,
                        UUID.fromString(QUEUE_ID),
                        "16.5",
                        1773524078235L,
                        1362L,
                        List.of(
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "h_gAfdHVoI8vBJaIyyGOUqDJlXUGGePLUJdMpR1zoRVPdigrRKvMchFBAzIlOUpd4BLlr6Ba8BRSjw",
                                                "Maynter",
                                                "EUW",
                                                1301,
                                                787),
                                        1,
                                        5,
                                        1,
                                        3776,
                                        64721,
                                        7,
                                        6451,
                                        Role.TOP,
                                        Champion.GNAR,
                                        6.1,
                                        139,
                                        0,
                                        0,
                                        0,
                                        TeamSide.BLUE,
                                        false,
                                        1),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "OR5LGqOXhHVe8xj0IWrZHJWvOqLMVprQYTKbhawJQjzIFjDaFwCmnLc8Eid0rWTsZ6DRVCz65lsjrw",
                                                "Fentasm",
                                                "FENT",
                                                6278,
                                                656),
                                        1,
                                        7,
                                        4,
                                        5874,
                                        142137,
                                        4,
                                        8100,
                                        Role.JUNGLE,
                                        Champion.VAYNE,
                                        1.5,
                                        33,
                                        0,
                                        0,
                                        0,
                                        TeamSide.BLUE,
                                        false,
                                        2),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "aqzT2pS2aQuBrkKv4izgg99_X73edM43rJoSbOhEhWg1lThgMB4yDcQsU8hGtXVTZnQmAYOawLMxeA",
                                                "SlayderW",
                                                "EUWW",
                                                28,
                                                103),
                                        4,
                                        5,
                                        2,
                                        759,
                                        122557,
                                        5,
                                        9823,
                                        Role.MID,
                                        Champion.ORIANNA,
                                        9.3,
                                        211,
                                        0,
                                        0,
                                        0,
                                        TeamSide.BLUE,
                                        false,
                                        3),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "neqSH5XmjVkKkOki4MDq5Re0WwHMJ8BKsh_URZjThuWOINOoYvoe-YNU8Ll48ojGN5oCn4owcd2UQg",
                                                "KISS OF DEATH",
                                                "YEN",
                                                1455,
                                                476),
                                        6,
                                        9,
                                        4,
                                        1315,
                                        69636,
                                        7,
                                        9597,
                                        Role.BOTTOM,
                                        Champion.ASHE,
                                        6.4,
                                        146,
                                        0,
                                        0,
                                        0,
                                        TeamSide.BLUE,
                                        false,
                                        4),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "5GC_pKq053P0rngh7uTcjwB79V4vs1JoA3wzcUYgqlRr6tB-iNEF51CJnOQKrk4daGa5g8AdNezprw",
                                                "Last dance",
                                                "Hexom",
                                                1637,
                                                106),
                                        1,
                                        9,
                                        8,
                                        4690,
                                        23399,
                                        24,
                                        6350,
                                        Role.SUPPORT,
                                        Champion.SERAPHINE,
                                        1.3,
                                        29,
                                        0,
                                        0,
                                        0,
                                        TeamSide.BLUE,
                                        false,
                                        5),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "Yhpm3VDVVQ09nAF9qUANOLFyItHikNj54vxB1yMzHmqIEohkIKEAeX-al94bBnYWtmsYv5CN6W3BvQ",
                                                "baby kiin",
                                                "keem",
                                                6,
                                                120),
                                        5,
                                        3,
                                        5,
                                        2833,
                                        144290,
                                        10,
                                        11484,
                                        Role.TOP,
                                        Champion.JAYCE,
                                        8.4,
                                        191,
                                        0,
                                        0,
                                        0,
                                        TeamSide.RED,
                                        true,
                                        -1),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "avrrbs2xmpx-CjjNFWyRENlXRmLPTN4X-igl6r-I732hIPmTV_e5NlJ3W4qVJ58inDq4hY_6e5sqOA",
                                                "J1HUIV",
                                                "000",
                                                29,
                                                445),
                                        11,
                                        1,
                                        6,
                                        9874,
                                        295508,
                                        4,
                                        13424,
                                        Role.JUNGLE,
                                        Champion.QIYANA,
                                        1.7,
                                        39,
                                        1,
                                        0,
                                        0,
                                        TeamSide.RED,
                                        true,
                                        -2),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "CFcq57-yO1Shwn5cVI0nkIHWIGgzl-QV1g5qMAliHHZuFo_HBoX-S6ZjhidAKLenTr5ziJAHXxw-aQ",
                                                "Yoriichı",
                                                "EUW",
                                                5045,
                                                210),
                                        5,
                                        1,
                                        5,
                                        6144,
                                        140039,
                                        5,
                                        10871,
                                        Role.MID,
                                        Champion.AHRI,
                                        9.7,
                                        221,
                                        0,
                                        0,
                                        0,
                                        TeamSide.RED,
                                        true,
                                        -3),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "vyzwo-B74dAgHNsstNoWPioVU8bLh6kye-0xtdFTMQwM4Y-kCSZfwMpf29CadJJ1AvpYEPV5yNMicA",
                                                "KC NEXT ADKING",
                                                "EUW",
                                                29,
                                                727),
                                        13,
                                        3,
                                        12,
                                        12959,
                                        110972,
                                        10,
                                        13924,
                                        Role.BOTTOM,
                                        Champion.SENNA,
                                        7.3,
                                        165,
                                        0,
                                        0,
                                        0,
                                        TeamSide.RED,
                                        true,
                                        -4),
                                new PlayerStatsDTO(
                                        new RiotId(
                                                "fwP-3K-Z_ghLAruQYlntd03YSdRCJeD7kaJQFujGJJpiwZ-ie9Rwkc9SmsiExqcnbnssXGmoAtyqHA",
                                                "berebooo",
                                                "TR1",
                                                3158,
                                                346),
                                        1,
                                        6,
                                        26,
                                        3544,
                                        15953,
                                        33,
                                        8253,
                                        Role.SUPPORT,
                                        Champion.BLITZCRANK,
                                        0.7,
                                        15,
                                        0,
                                        0,
                                        0,
                                        TeamSide.RED,
                                        true,
                                        -5)),
                        List.of(
                                new TeamStatsDTO(TeamSide.BLUE, 3, 35, 19, 16414),
                                new TeamStatsDTO(TeamSide.RED, 35, 14, 54, 35354)));

        mmrDeltas =
                Map.of(
                        "h_gAfdHVoI8vBJaIyyGOUqDJlXUGGePLUJdMpR1zoRVPdigrRKvMchFBAzIlOUpd4BLlr6Ba8BRSjw", 1,
                        "OR5LGqOXhHVe8xj0IWrZHJWvOqLMVprQYTKbhawJQjzIFjDaFwCmnLc8Eid0rWTsZ6DRVCz65lsjrw", 2,
                        "aqzT2pS2aQuBrkKv4izgg99_X73edM43rJoSbOhEhWg1lThgMB4yDcQsU8hGtXVTZnQmAYOawLMxeA", 3,
                        "neqSH5XmjVkKkOki4MDq5Re0WwHMJ8BKsh_URZjThuWOINOoYvoe-YNU8Ll48ojGN5oCn4owcd2UQg", 4,
                        "5GC_pKq053P0rngh7uTcjwB79V4vs1JoA3wzcUYgqlRr6tB-iNEF51CJnOQKrk4daGa5g8AdNezprw", 5,
                        "Yhpm3VDVVQ09nAF9qUANOLFyItHikNj54vxB1yMzHmqIEohkIKEAeX-al94bBnYWtmsYv5CN6W3BvQ", -1,
                        "avrrbs2xmpx-CjjNFWyRENlXRmLPTN4X-igl6r-I732hIPmTV_e5NlJ3W4qVJ58inDq4hY_6e5sqOA", -2,
                        "CFcq57-yO1Shwn5cVI0nkIHWIGgzl-QV1g5qMAliHHZuFo_HBoX-S6ZjhidAKLenTr5ziJAHXxw-aQ", -3,
                        "vyzwo-B74dAgHNsstNoWPioVU8bLh6kye-0xtdFTMQwM4Y-kCSZfwMpf29CadJJ1AvpYEPV5yNMicA", -4,
                        "fwP-3K-Z_ghLAruQYlntd03YSdRCJeD7kaJQFujGJJpiwZ-ie9Rwkc9SmsiExqcnbnssXGmoAtyqHA", -5);

        mmrDeltas2 =
                Map.of(
                        "18qWu0pwwA_sfCE8Ih_jIJhJiC7TlRK1dHXsKCZ_mFIAYPMf5-YQKH7gUFlDE9Fh-YZtYDsRMmySYg", 1,
                        "M6xuDdkAxNyajMiFto9VTsdP_JTatZlz4xZ06sd-UzTHWuzcyKE23C0bR6zSKSmvR0mfvuciU-NFZQ", 2,
                        "oM5fTuy-bSHFjW_CJJ5_nYolZ1WTFge_0_caldn2ylpUiJV6glA9RfmNDE35xHyakwejILdYMfBUOg", 3,
                        "g9evN71NQIXfaQYH5ZdYMwZbEeg1wni_uiyQh__G0iD3bnz7yjHVA-Xi341gdhNpQ25hd58N1dth4w", 4,
                        "yzV2y2SXD9LYlAiiJ8Rp3ehgDw5vSYHg-g1ai7ftYw4Ap2_Rz6fuKyyC-hh6ELcj-PwFueE4irOLDw", 5,
                        "lCNhDWC2_gIx4v9nV8VsaBkLnUzwTCERUlP4WwQ5IwIE2gbv1MHkXkfjNs3Nm2tKXmmbM_I9TaNhIA", -1,
                        "fyBU5HDdwtPD0bnPxU0Do7aDxT1voRiqyJH9m4m10R9E9Gkhx88TcY0AuWFNqLfefkw2xmQAIlpZXw", -2,
                        "EIOzLS5LzdIUswGZu39ERMRucu0NOz4OzM3g4P1tkfxPA8jUHeO31DbkwOLddLzQABdwZ0Lx7qC9LQ", -3,
                        "wg70K5vl52HTn_wvM15Ww62K_-xY9iHaV4L4Ua-_yRx-ApUosLJQdYrzIjHNCaz5O4UbIe-6griPwQ", -4,
                        "k_-JlBGNZWdw8j9DFKrEJgdqF8DTrpAZxqyLnbObjq__klQIyvCNvdxl8wvJ2I5uvYnkfKSXnnOlEQ", -5);
    }

    @Test
    @Order(1)
    public void setupMatch1() {
        when(detailFillingAdapterService.getMatch(MATCH_ID)).thenReturn(matchDTO);
        when(trainingAdapterService.sendMatchForAnalysis(MATCH_ID)).thenReturn(true);
        assertEquals(
                matchDTO, matchHistoryService.addMatchById(MATCH_ID, UUID.fromString(QUEUE_ID), mmrDeltas));
        verify(detailFillingAdapterService).getMatch(MATCH_ID);
        verify(trainingAdapterService).sendMatchForAnalysis(MATCH_ID);
    }

    @Test
    @Order(2)
    public void
            testFilterSortMatches_ShouldThrowInvalidPaginationParametersException_WhenPageIsNegative() {
        int page = -1;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        assertThrows(
                InvalidPaginationParametersException.class,
                () -> matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(3)
    public void
            testFilterSortMatches_ShouldThrowInvalidPaginationParametersException_WhenSizeIsLowerThan1() {
        int page = 0;
        int size = 0;

        MatchFilters filters = new MatchFilters();

        assertThrows(
                InvalidPaginationParametersException.class,
                () -> matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(4)
    public void
            testFilterSortMatches_ShouldThrowInvalidPaginationParametersException_WhenSizeIsGreaterThan100() {
        int page = 0;
        int size = 101;

        MatchFilters filters = new MatchFilters();

        assertThrows(
                InvalidPaginationParametersException.class,
                () -> matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(5)
    public void
            testFilterSortMatches_ShouldThrowInvalidIntervalException_WhenPatchMaxIsGreaterThanMin() {
        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setPatch(new PatchInterval("15.1", "15.0"));

        assertThrows(
                InvalidIntervalException.class,
                () -> matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(6)
    public void
            testFilterSortMatches_ShouldThrowInvalidIntervalException_WhenTimeMaxIsGreaterThanMin() {
        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setTime(new LongInterval(1234L, 1233L));

        assertThrows(
                InvalidIntervalException.class,
                () -> matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(7)
    public void
            testFilterSortMatches_ShouldThrowInvalidIntervalException_WhenPlayerKillsMaxIsGreaterThanMin() {
        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        PlayerFilters playerFilters = new PlayerFilters();
        playerFilters.setKills(new IntegerInterval(21, 20));

        filters.setPlayers(List.of(playerFilters));

        assertThrows(
                InvalidIntervalException.class,
                () -> matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(8)
    public void
            testFilterSortMatches_ShouldThrowInvalidIntervalException_WhenPlayerDeathsMaxIsGreaterThanMin() {
        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        PlayerFilters playerFilters = new PlayerFilters();
        playerFilters.setDeaths(new IntegerInterval(21, 20));

        filters.setPlayers(List.of(playerFilters));

        assertThrows(
                InvalidIntervalException.class,
                () -> matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(9)
    public void
            testFilterSortMatches_ShouldThrowInvalidIntervalException_WhenPlayerAssistsMaxIsGreaterThanMin() {
        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        PlayerFilters playerFilters = new PlayerFilters();
        playerFilters.setAssists(new IntegerInterval(21, 20));

        filters.setPlayers(List.of(playerFilters));

        assertThrows(
                InvalidIntervalException.class,
                () -> matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(10)
    public void
            testFilterSortMatches_ShouldThrowInvalidIntervalException_WhenPlayerHealingMaxIsGreaterThanMin() {
        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        PlayerFilters playerFilters = new PlayerFilters();
        playerFilters.setHealing(new IntegerInterval(21, 20));

        filters.setPlayers(List.of(playerFilters));

        assertThrows(
                InvalidIntervalException.class,
                () -> matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(11)
    public void
            testFilterSortMatches_ShouldThrowInvalidIntervalException_WhenPlayerDamageToPlayersMaxIsGreaterThanMin() {
        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        PlayerFilters playerFilters = new PlayerFilters();
        playerFilters.setDamageToPlayers(new IntegerInterval(21, 20));

        filters.setPlayers(List.of(playerFilters));

        assertThrows(
                InvalidIntervalException.class,
                () -> matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(12)
    public void
            testFilterSortMatches_ShouldThrowInvalidIntervalException_WhenPlayerWardsMaxIsGreaterThanMin() {
        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        PlayerFilters playerFilters = new PlayerFilters();
        playerFilters.setWards(new IntegerInterval(21, 20));

        filters.setPlayers(List.of(playerFilters));

        assertThrows(
                InvalidIntervalException.class,
                () -> matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(13)
    public void
            testFilterSortMatches_ShouldThrowInvalidIntervalException_WhenPlayerGoldMaxIsGreaterThanMin() {
        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        PlayerFilters playerFilters = new PlayerFilters();
        playerFilters.setGold(new IntegerInterval(21, 20));

        filters.setPlayers(List.of(playerFilters));

        assertThrows(
                InvalidIntervalException.class,
                () -> matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(14)
    public void
            testFilterSortMatches_ShouldThrowInvalidIntervalException_WhenPlayerCsPerMinuteMaxIsGreaterThanMin() {
        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        PlayerFilters playerFilters = new PlayerFilters();
        playerFilters.setCsPerMinute(new DoubleInterval(21.0, 20.0));

        filters.setPlayers(List.of(playerFilters));

        assertThrows(
                InvalidIntervalException.class,
                () -> matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(15)
    public void
            testFilterSortMatches_ShouldThrowInvalidIntervalException_WhenPlayerKilledMinionsMaxIsGreaterThanMin() {
        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        PlayerFilters playerFilters = new PlayerFilters();
        playerFilters.setKilledMinions(new IntegerInterval(21, 20));

        filters.setPlayers(List.of(playerFilters));

        assertThrows(
                InvalidIntervalException.class,
                () -> matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(16)
    public void
            testFilterSortMatches_ShouldThrowInvalidIntervalException_WhenPlayerTripleKillsMaxIsGreaterThanMin() {
        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        PlayerFilters playerFilters = new PlayerFilters();
        playerFilters.setTripleKills(new IntegerInterval(21, 20));

        filters.setPlayers(List.of(playerFilters));

        assertThrows(
                InvalidIntervalException.class,
                () -> matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(17)
    public void
            testFilterSortMatches_ShouldThrowInvalidIntervalException_WhenPlayerQuadKillsMaxIsGreaterThanMin() {
        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        PlayerFilters playerFilters = new PlayerFilters();
        playerFilters.setQuadKills(new IntegerInterval(21, 20));

        filters.setPlayers(List.of(playerFilters));

        assertThrows(
                InvalidIntervalException.class,
                () -> matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(18)
    public void
            testFilterSortMatches_ShouldThrowInvalidIntervalException_WhenPlayerPentaKillsMaxIsGreaterThanMin() {
        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        PlayerFilters playerFilters = new PlayerFilters();
        playerFilters.setPentaKills(new IntegerInterval(21, 20));

        filters.setPlayers(List.of(playerFilters));

        assertThrows(
                InvalidIntervalException.class,
                () -> matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(19)
    public void
            testFilterSortMatches_ShouldThrowInvalidIntervalException_WhenPlayerMMRDeltaMaxIsGreaterThanMin() {
        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        PlayerFilters playerFilters = new PlayerFilters();
        playerFilters.setMmrDelta(new IntegerInterval(21, 20));

        filters.setPlayers(List.of(playerFilters));

        assertThrows(
                InvalidIntervalException.class,
                () -> matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(20)
    public void
            testFilterSortMatches_ShouldThrowInvalidIntervalException_WhenTeamKillsMaxIsGreaterThanMin() {
        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        TeamFilters teamFilters = new TeamFilters();
        teamFilters.setTeamKills(new IntegerInterval(21, 20));

        filters.setTeams(List.of(teamFilters));

        assertThrows(
                InvalidIntervalException.class,
                () -> matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(21)
    public void
            testFilterSortMatches_ShouldThrowInvalidIntervalException_WhenTeamDeathsMaxIsGreaterThanMin() {
        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        TeamFilters teamFilters = new TeamFilters();
        teamFilters.setTeamDeaths(new IntegerInterval(21, 20));

        filters.setTeams(List.of(teamFilters));

        assertThrows(
                InvalidIntervalException.class,
                () -> matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(22)
    public void
            testFilterSortMatches_ShouldThrowInvalidIntervalException_WhenTeamAssistsMaxIsGreaterThanMin() {
        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        TeamFilters teamFilters = new TeamFilters();
        teamFilters.setTeamAssists(new IntegerInterval(21, 20));

        filters.setTeams(List.of(teamFilters));

        assertThrows(
                InvalidIntervalException.class,
                () -> matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(23)
    public void
            testFilterSortMatches_ShouldThrowInvalidIntervalException_WhenTeamHealingMaxIsGreaterThanMin() {
        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        TeamFilters teamFilters = new TeamFilters();
        teamFilters.setTeamHealing(new IntegerInterval(21, 20));

        filters.setTeams(List.of(teamFilters));

        assertThrows(
                InvalidIntervalException.class,
                () -> matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(24)
    public void testFilterSortMatches_ShouldReturnMatchDTO_WhenFilterEmpty() {
        // Filter empty

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO), page, 1, 1);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(25)
    public void testFilterSortMatches_ShouldReturnMatchDTO_WhenFilterByExistentQueueId() {
        // Filter by queueId = QUEUE_ID

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setQueueId(UUID.fromString(QUEUE_ID));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO), page, 1, 1);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(26)
    public void testFilterSortMatches_ShouldReturnEmpty_WhenFilterByNonExistentQueueId() {
        // Filter by queueId = QUEUE_ID2 (no match)

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setQueueId(UUID.fromString(QUEUE_ID2));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(), page, 0, 0);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(26)
    public void testFilterSortMatches_ShouldReturnMatchDTO_WhenFilterByExistentTimeMin() {
        // Filter by time >= 10000

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setTime(new LongInterval(10000L, null));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO), page, 1, 1);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(27)
    public void
            testFilterSortMatches_ShouldReturnMatchDTO_WhenFilterByExistentTimeMinInsideMatchInterval() {
        // Filter by time >= 1773524078335
        // Match is between 1773524078235 and 1773524079597 (inclusive)

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setQueueId(UUID.fromString(QUEUE_ID));
        filters.setTime(new LongInterval(1773524078335L, null));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO), page, 1, 1);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(28)
    public void testFilterSortMatches_ShouldReturnEmpty_WhenFilterByNonExistentTimeMin() {
        // Filter by time >= 1873524078235 (no match)
        // Match is between 1773524078235 and 1773524079597 (inclusive)

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setQueueId(UUID.fromString(QUEUE_ID));
        filters.setTime(new LongInterval(1873524078235L, null));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(), page, 0, 0);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(29)
    public void testFilterSortMatches_ShouldReturnMatchDTO_WhenFilterByExistentTimeMax() {
        // Filter by time <= 1873524078235
        // Match is between 1773524078235 and 1773524079597 (inclusive)

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setQueueId(UUID.fromString(QUEUE_ID));
        filters.setTime(new LongInterval(null, 1873524078235L));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO), page, 1, 1);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(29)
    public void testFilterSortMatches_ShouldReturnEmpty_WhenFilterByNonExistentTimeMax() {
        // Filter by time <= 1673524078235 (no match)
        // Match is between 1773524078235 and 1773524079597 (inclusive)

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setQueueId(UUID.fromString(QUEUE_ID));
        filters.setTime(new LongInterval(null, 1673524078235L));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(), page, 0, 0);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(30)
    public void
            testFilterSortMatches_ShouldReturnMatchDTO_WhenFilterByExistentTimeWhichIsMatchStart() {
        // Filter by time = 1773524078235
        // Match is between 1773524078235 and 1773524079597 (inclusive)

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setQueueId(UUID.fromString(QUEUE_ID));
        filters.setTime(new LongInterval(1773524078235L, 1773524078235L));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO), page, 1, 1);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(31)
    public void testFilterSortMatches_ShouldReturnMatchDTO_WhenFilterByExistentTimeWhichIsMatchEnd() {
        // Filter by time = 1773524079597
        // Match is between 1773524078235 and 1773524079597 (inclusive)

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setQueueId(UUID.fromString(QUEUE_ID));
        filters.setTime(new LongInterval(1773524079597L, 1773524079597L));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO), page, 1, 1);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(32)
    public void testFilterSortMatches_ShouldReturnEmpty_WhenFilterByNonExistentTime() {
        // Filter by time = 1773524079598 (no match)
        // Match is between 1773524078235 and 1773524079597 (inclusive)

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setQueueId(UUID.fromString(QUEUE_ID));
        filters.setTime(new LongInterval(1773524079598L, 1773524079598L));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(), page, 0, 0);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(33)
    public void testFilterSortMatches_ShouldReturnMatchDTO_WhenFilterByExistentChampion() {
        // Filter by matches with champion GNAR
        // Match has champions: GNAR, VAYNE, ORIANNA, ASHE, SERAPHINE, JAYCE, QIYANA, AHRI, SENNA and
        // BLITZCRANK

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setQueueId(UUID.fromString(QUEUE_ID));
        filters.setTime(new LongInterval(1773524079597L, 1773524079597L));
        filters.setChampions(List.of(Champion.GNAR));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO), page, 1, 1);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(34)
    public void testFilterSortMatches_ShouldReturnEmpty_WhenFilterByNonExistentChampion() {
        // Filter by matches with champion JINX (no match)
        // Match has champions: GNAR, VAYNE, ORIANNA, ASHE, SERAPHINE, JAYCE, QIYANA, AHRI, SENNA and
        // BLITZCRANK

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setQueueId(UUID.fromString(QUEUE_ID));
        filters.setTime(new LongInterval(1773524079597L, 1773524079597L));
        filters.setChampions(List.of(Champion.JINX));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(), page, 0, 0);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(35)
    public void testFilterSortMatches_ShouldReturnEmpty_WhenFilterByExistentAndNonExistentChampion() {
        // Filter by matches with champions GNAR and JINX (no match)
        // Match has champions: GNAR, VAYNE, ORIANNA, ASHE, SERAPHINE, JAYCE, QIYANA, AHRI, SENNA and
        // BLITZCRANK

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setQueueId(UUID.fromString(QUEUE_ID));
        filters.setTime(new LongInterval(1773524079597L, 1773524079597L));
        filters.setChampions(List.of(Champion.GNAR, Champion.JINX));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(), page, 0, 0);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(36)
    public void testFilterSortMatches_ShouldReturnMatchDTO_WhenFilterByTwoExistentChampions() {
        // Filter by matches with champion ASHE and BLITZCRANK
        // Match has champions: GNAR, VAYNE, ORIANNA, ASHE, SERAPHINE, JAYCE, QIYANA, AHRI, SENNA and
        // BLITZCRANK

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setQueueId(UUID.fromString(QUEUE_ID));
        filters.setTime(new LongInterval(1773524079597L, 1773524079597L));
        filters.setChampions(List.of(Champion.ASHE, Champion.BLITZCRANK));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO), page, 1, 1);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(37)
    public void testFilterSortMatches_ShouldReturnMatchDTO_WhenFilterByExistentPatchMin() {
        // Filter by matches made since patch 16.0
        // Match was made in patch 16.5

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setPatch(new PatchInterval("16.0", null));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO), page, 1, 1);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(38)
    public void testFilterSortMatches_ShouldReturnEmpty_WhenFilterByNonExistentPatchMin() {
        // Filter by matches made since patch 16.6 (no match)
        // Match was made in patch 16.5

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setPatch(new PatchInterval("16.6", null));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(), page, 0, 0);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(39)
    public void testFilterSortMatches_ShouldReturnEmpty_WhenFilterByNonExistentPatchMax() {
        // Filter by matches made until patch 16.4 (no match)
        // Match was made in patch 16.5

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setPatch(new PatchInterval(null, "16.4"));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(), page, 0, 0);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(40)
    public void testFilterSortMatches_ShouldReturnMatchDTO_WhenFilterByExistentPatchMax() {
        // Filter by matches made until patch 16.5
        // Match was made in patch 16.5

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setPatch(new PatchInterval(null, "16.5"));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO), page, 1, 1);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(41)
    public void
            testFilterSortMatches_ShouldReturnEmpty_WhenFilterByNonExistentPatchMinWithExtraParts() {
        // Filter by matches made since patch 16.5.1 (no match)
        // Match was made in patch 16.5

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setPatch(new PatchInterval("16.5.1", null));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(), page, 0, 0);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(42)
    public void
            testFilterSortMatches_ShouldReturnEmpty_WhenFilterByNonExistentPatchBetweenMinAndMax() {
        // Filter by matches made between patches 16 and 16.3 (no match)
        // Match was made in patch 16.5

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setPatch(new PatchInterval("16", "16.3"));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(), page, 0, 0);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(43)
    public void
            testFilterSortMatches_ShouldReturnMatchDTO_WhenFilterByExistentPatchBetweenMinAndMax() {
        // Filter by matches made between patches 16 and 17.8
        // Match was made in patch 16.5

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setPatch(new PatchInterval("16", "17.8"));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO), page, 1, 1);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(44)
    public void testFilterSortMatches_ShouldReturnMatchDTO_WhenFilterByExistentPlayerWithKillsMin() {
        // Filter by matches with some player having more than 5 kills

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        PlayerFilters playerFilters = new PlayerFilters();
        playerFilters.setKills(new IntegerInterval(5, null));

        filters.setPlayers(List.of(playerFilters));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO), page, 1, 1);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(45)
    public void
            testFilterSortMatches_ShouldReturnMatchDTO_WhenFilterByExistentPlayerWithPlayerName() {
        // Filter by matches with player KISS OF DEATH
        // Match has players: Maynter, Fentasm, SlayderW, KISS OF DEATH, Last dance,
        // baby kiin, J1HUIV, Yoriich1, KC NEXT ADKING and berebooo

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        PlayerFilters playerFilters = new PlayerFilters();
        playerFilters.setPlayerName("KISS OF DEATH");

        filters.setPlayers(List.of(playerFilters));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO), page, 1, 1);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(46)
    public void
            testFilterSortMatches_ShouldReturnMatchDTO_WhenFilterByExistentPlayerWithPlayerNameAndRole() {
        // Filter by matches with player Maynter having role TOP

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        PlayerFilters playerFilters = new PlayerFilters();
        playerFilters.setPlayerName("Maynter");
        playerFilters.setRole(Role.TOP);

        filters.setPlayers(List.of(playerFilters));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO), page, 1, 1);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(47)
    public void
            testFilterSortMatches_ShouldReturnMatchDTO_WhenFilterByTwoExistentPlayersWithValidRestrictions() {
        // Filter by matches with player Last dance having role SUPPORT
        // on the blue team with less than 5000 healing and
        // player berebooo winning with a single kill and more than 8000 gold

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        PlayerFilters playerFilters1 = new PlayerFilters();
        playerFilters1.setPlayerName("Last dance");
        playerFilters1.setRole(Role.SUPPORT);
        playerFilters1.setTeamSide(TeamSide.BLUE);
        playerFilters1.setHealing(new IntegerInterval(null, 5000));

        PlayerFilters playerFilters2 = new PlayerFilters();
        playerFilters2.setPlayerName("berebooo");
        playerFilters2.setWon(true);
        playerFilters2.setKills(new IntegerInterval(1, 1));
        playerFilters2.setGold(new IntegerInterval(8000, null));

        filters.setPlayers(List.of(playerFilters1, playerFilters2));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO), page, 1, 1);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(48)
    public void
            testFilterSortMatches_ShouldReturnMatchDTO_WhenFilterByExistentMatchChampionAndExistentPlayerChampion() {
        // Filter by matches where someone played as champion ASHE and
        // player Fentasm played as one of the following champions: VAYNE, DIANA or JAX

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setChampions(List.of(Champion.ASHE));

        PlayerFilters playerFilters = new PlayerFilters();
        playerFilters.setPlayerName("Fentasm");
        playerFilters.setChampions(List.of(Champion.VAYNE, Champion.DIANA, Champion.JAX));

        filters.setPlayers(List.of(playerFilters));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO), page, 1, 1);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(49)
    public void testFilterSortMatches_ShouldReturnMatchDTO_WhenFilterByExistentTeamWithKillsMin() {
        // Filter by matches where some team had at least 20 kills
        // Match has team blue with 13 kills and team red with 35 kills

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        TeamFilters teamFilters = new TeamFilters();
        teamFilters.setTeamKills(new IntegerInterval(20, null));

        filters.setTeams(List.of(teamFilters));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO), page, 1, 1);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(50)
    public void
            testFilterSortMatches_ShouldReturnMatchDTO_WhenFilterByExistentTeamWithKillsMinAndTeamBlueWithValidRestrictions() {
        // Filter by matches where some team had at least 20 kills and
        // team blue had at most 15 kills and between 30 and 40 deaths
        // Match has team blue with 13 kills and team red with 35 kills

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        TeamFilters teamFilters1 = new TeamFilters();
        teamFilters1.setTeamKills(new IntegerInterval(20, null));

        TeamFilters teamFilters2 = new TeamFilters();
        teamFilters2.setTeamSide(TeamSide.BLUE);
        teamFilters2.setTeamKills(new IntegerInterval(null, 15));
        teamFilters2.setTeamDeaths(new IntegerInterval(30, 40));

        filters.setTeams(List.of(teamFilters1, teamFilters2));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO), page, 1, 1);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(51)
    public void
            testFilterSortMatches_ShouldReturnMatchDTO_WhenFilterByExistentPlayersInEachTeamWithTheirWinStatus() {
        // Filter by matches where player Maynter lost on the blue team and
        // player baby kiin won on the red team

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();

        PlayerFilters playerFilters1 = new PlayerFilters();
        playerFilters1.setPlayerName("Maynter");
        playerFilters1.setTeamSide(TeamSide.BLUE);
        playerFilters1.setWon(false);

        PlayerFilters playerFilters2 = new PlayerFilters();
        playerFilters2.setPlayerName("baby kiin");
        playerFilters2.setTeamSide(TeamSide.RED);
        playerFilters2.setWon(true);

        filters.setPlayers(List.of(playerFilters1, playerFilters2));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO), page, 1, 1);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(52)
    public void testFilterSortMatches_ShouldReturnMatchDTO_WhenFilterByEverythingFromMatch() {
        // Filter by everything from match

        final int page = 0;
        final int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setQueueId(UUID.fromString(QUEUE_ID));
        filters.setPatch(new PatchInterval("16.5", "16.5"));
        filters.setTime(new LongInterval(1773524078235L, 1773524079597L));
        filters.setChampions(
                List.of(
                        Champion.GNAR,
                        Champion.VAYNE,
                        Champion.ORIANNA,
                        Champion.ASHE,
                        Champion.SERAPHINE,
                        Champion.JAYCE,
                        Champion.QIYANA,
                        Champion.AHRI,
                        Champion.SENNA,
                        Champion.BLITZCRANK));

        PlayerFilters playerFilters1 = new PlayerFilters();
        playerFilters1.setPuuid(
                "h_gAfdHVoI8vBJaIyyGOUqDJlXUGGePLUJdMpR1zoRVPdigrRKvMchFBAzIlOUpd4BLlr6Ba8BRSjw");
        playerFilters1.setPlayerName("Maynter");
        playerFilters1.setPlayerTag("EUW");
        playerFilters1.setSummonerIcon(1301);
        playerFilters1.setSummonerLevel(new IntegerInterval(787, 787));
        playerFilters1.setKills(new IntegerInterval(1, 1));
        playerFilters1.setDeaths(new IntegerInterval(5, 5));
        playerFilters1.setAssists(new IntegerInterval(1, 1));
        playerFilters1.setHealing(new IntegerInterval(3776, 3776));
        playerFilters1.setDamageToPlayers(new IntegerInterval(64721, 64721));
        playerFilters1.setWards(new IntegerInterval(7, 7));
        playerFilters1.setGold(new IntegerInterval(6451, 6451));
        playerFilters1.setRole(Role.TOP);
        playerFilters1.setChampions(List.of(Champion.GNAR));
        playerFilters1.setCsPerMinute(new DoubleInterval(6.1, 6.1));
        playerFilters1.setKilledMinions(new IntegerInterval(139, 139));
        playerFilters1.setTripleKills(new IntegerInterval(0, 0));
        playerFilters1.setQuadKills(new IntegerInterval(0, 0));
        playerFilters1.setPentaKills(new IntegerInterval(0, 0));
        playerFilters1.setTeamSide(TeamSide.BLUE);
        playerFilters1.setWon(false);

        PlayerFilters playerFilters2 = new PlayerFilters();
        playerFilters2.setPuuid(
                "OR5LGqOXhHVe8xj0IWrZHJWvOqLMVprQYTKbhawJQjzIFjDaFwCmnLc8Eid0rWTsZ6DRVCz65lsjrw");
        playerFilters2.setPlayerName("Fentasm");
        playerFilters2.setPlayerTag("FENT");
        playerFilters2.setSummonerIcon(6278);
        playerFilters2.setSummonerLevel(new IntegerInterval(656, 656));
        playerFilters2.setKills(new IntegerInterval(1, 1));
        playerFilters2.setDeaths(new IntegerInterval(7, 7));
        playerFilters2.setAssists(new IntegerInterval(4, 4));
        playerFilters2.setHealing(new IntegerInterval(5874, 5874));
        playerFilters2.setDamageToPlayers(new IntegerInterval(142137, 142137));
        playerFilters2.setWards(new IntegerInterval(4, 4));
        playerFilters2.setGold(new IntegerInterval(8100, 8100));
        playerFilters2.setRole(Role.JUNGLE);
        playerFilters2.setChampions(List.of(Champion.VAYNE));
        playerFilters2.setCsPerMinute(new DoubleInterval(1.5, 1.5));
        playerFilters2.setKilledMinions(new IntegerInterval(33, 33));
        playerFilters2.setTripleKills(new IntegerInterval(0, 0));
        playerFilters2.setQuadKills(new IntegerInterval(0, 0));
        playerFilters2.setPentaKills(new IntegerInterval(0, 0));
        playerFilters2.setTeamSide(TeamSide.BLUE);
        playerFilters2.setWon(false);

        PlayerFilters playerFilters3 = new PlayerFilters();
        playerFilters3.setPuuid(
                "aqzT2pS2aQuBrkKv4izgg99_X73edM43rJoSbOhEhWg1lThgMB4yDcQsU8hGtXVTZnQmAYOawLMxeA");
        playerFilters3.setPlayerName("SlayderW");
        playerFilters3.setPlayerTag("EUWW");
        playerFilters3.setSummonerIcon(28);
        playerFilters3.setSummonerLevel(new IntegerInterval(103, 103));
        playerFilters3.setKills(new IntegerInterval(4, 4));
        playerFilters3.setDeaths(new IntegerInterval(5, 5));
        playerFilters3.setAssists(new IntegerInterval(2, 2));
        playerFilters3.setHealing(new IntegerInterval(759, 759));
        playerFilters3.setDamageToPlayers(new IntegerInterval(122557, 122557));
        playerFilters3.setWards(new IntegerInterval(5, 5));
        playerFilters3.setGold(new IntegerInterval(9823, 9823));
        playerFilters3.setRole(Role.MID);
        playerFilters3.setChampions(List.of(Champion.ORIANNA));
        playerFilters3.setCsPerMinute(new DoubleInterval(9.3, 9.3));
        playerFilters3.setKilledMinions(new IntegerInterval(211, 211));
        playerFilters3.setTripleKills(new IntegerInterval(0, 0));
        playerFilters3.setQuadKills(new IntegerInterval(0, 0));
        playerFilters3.setPentaKills(new IntegerInterval(0, 0));
        playerFilters3.setTeamSide(TeamSide.BLUE);
        playerFilters3.setWon(false);

        PlayerFilters playerFilters4 = new PlayerFilters();
        playerFilters4.setPuuid(
                "neqSH5XmjVkKkOki4MDq5Re0WwHMJ8BKsh_URZjThuWOINOoYvoe-YNU8Ll48ojGN5oCn4owcd2UQg");
        playerFilters4.setPlayerName("KISS OF DEATH");
        playerFilters4.setPlayerTag("YEN");
        playerFilters4.setSummonerIcon(1455);
        playerFilters4.setSummonerLevel(new IntegerInterval(476, 476));
        playerFilters4.setKills(new IntegerInterval(6, 6));
        playerFilters4.setDeaths(new IntegerInterval(9, 9));
        playerFilters4.setAssists(new IntegerInterval(4, 4));
        playerFilters4.setHealing(new IntegerInterval(1315, 1315));
        playerFilters4.setDamageToPlayers(new IntegerInterval(69636, 69636));
        playerFilters4.setWards(new IntegerInterval(7, 7));
        playerFilters4.setGold(new IntegerInterval(9597, 9597));
        playerFilters4.setRole(Role.BOTTOM);
        playerFilters4.setChampions(List.of(Champion.ASHE));
        playerFilters4.setCsPerMinute(new DoubleInterval(6.4, 6.4));
        playerFilters4.setKilledMinions(new IntegerInterval(146, 146));
        playerFilters4.setTripleKills(new IntegerInterval(0, 0));
        playerFilters4.setQuadKills(new IntegerInterval(0, 0));
        playerFilters4.setPentaKills(new IntegerInterval(0, 0));
        playerFilters4.setTeamSide(TeamSide.BLUE);
        playerFilters4.setWon(false);

        PlayerFilters playerFilters5 = new PlayerFilters();
        playerFilters5.setPuuid(
                "5GC_pKq053P0rngh7uTcjwB79V4vs1JoA3wzcUYgqlRr6tB-iNEF51CJnOQKrk4daGa5g8AdNezprw");
        playerFilters5.setPlayerName("Last dance");
        playerFilters5.setPlayerTag("Hexom");
        playerFilters5.setSummonerIcon(1637);
        playerFilters5.setSummonerLevel(new IntegerInterval(106, 106));
        playerFilters5.setKills(new IntegerInterval(1, 1));
        playerFilters5.setDeaths(new IntegerInterval(9, 9));
        playerFilters5.setAssists(new IntegerInterval(8, 8));
        playerFilters5.setHealing(new IntegerInterval(4690, 4690));
        playerFilters5.setDamageToPlayers(new IntegerInterval(23399, 23399));
        playerFilters5.setWards(new IntegerInterval(24, 24));
        playerFilters5.setGold(new IntegerInterval(6350, 6350));
        playerFilters5.setRole(Role.SUPPORT);
        playerFilters5.setChampions(List.of(Champion.SERAPHINE));
        playerFilters5.setCsPerMinute(new DoubleInterval(1.3, 1.3));
        playerFilters5.setKilledMinions(new IntegerInterval(29, 29));
        playerFilters5.setTripleKills(new IntegerInterval(0, 0));
        playerFilters5.setQuadKills(new IntegerInterval(0, 0));
        playerFilters5.setPentaKills(new IntegerInterval(0, 0));
        playerFilters5.setTeamSide(TeamSide.BLUE);
        playerFilters5.setWon(false);

        PlayerFilters playerFilters6 = new PlayerFilters();
        playerFilters6.setPuuid(
                "Yhpm3VDVVQ09nAF9qUANOLFyItHikNj54vxB1yMzHmqIEohkIKEAeX-al94bBnYWtmsYv5CN6W3BvQ");
        playerFilters6.setPlayerName("baby kiin");
        playerFilters6.setPlayerTag("keem");
        playerFilters6.setSummonerIcon(6);
        playerFilters6.setSummonerLevel(new IntegerInterval(120, 120));
        playerFilters6.setKills(new IntegerInterval(5, 5));
        playerFilters6.setDeaths(new IntegerInterval(3, 3));
        playerFilters6.setAssists(new IntegerInterval(5, 5));
        playerFilters6.setHealing(new IntegerInterval(2833, 2833));
        playerFilters6.setDamageToPlayers(new IntegerInterval(144290, 144290));
        playerFilters6.setWards(new IntegerInterval(10, 10));
        playerFilters6.setGold(new IntegerInterval(11484, 11484));
        playerFilters6.setRole(Role.TOP);
        playerFilters6.setChampions(List.of(Champion.JAYCE));
        playerFilters6.setCsPerMinute(new DoubleInterval(8.4, 8.4));
        playerFilters6.setKilledMinions(new IntegerInterval(191, 191));
        playerFilters6.setTripleKills(new IntegerInterval(0, 0));
        playerFilters6.setQuadKills(new IntegerInterval(0, 0));
        playerFilters6.setPentaKills(new IntegerInterval(0, 0));
        playerFilters6.setTeamSide(TeamSide.RED);
        playerFilters6.setWon(true);

        PlayerFilters playerFilters7 = new PlayerFilters();
        playerFilters7.setPuuid(
                "avrrbs2xmpx-CjjNFWyRENlXRmLPTN4X-igl6r-I732hIPmTV_e5NlJ3W4qVJ58inDq4hY_6e5sqOA");
        playerFilters7.setPlayerName("J1HUIV");
        playerFilters7.setPlayerTag("000");
        playerFilters7.setSummonerIcon(29);
        playerFilters7.setSummonerLevel(new IntegerInterval(445, 445));
        playerFilters7.setKills(new IntegerInterval(11, 11));
        playerFilters7.setDeaths(new IntegerInterval(1, 1));
        playerFilters7.setAssists(new IntegerInterval(6, 6));
        playerFilters7.setHealing(new IntegerInterval(9874, 9874));
        playerFilters7.setDamageToPlayers(new IntegerInterval(295508, 295508));
        playerFilters7.setWards(new IntegerInterval(4, 4));
        playerFilters7.setGold(new IntegerInterval(13424, 13424));
        playerFilters7.setRole(Role.JUNGLE);
        playerFilters7.setChampions(List.of(Champion.QIYANA));
        playerFilters7.setCsPerMinute(new DoubleInterval(1.7, 1.7));
        playerFilters7.setKilledMinions(new IntegerInterval(39, 39));
        playerFilters7.setTripleKills(new IntegerInterval(1, 1));
        playerFilters7.setQuadKills(new IntegerInterval(0, 0));
        playerFilters7.setPentaKills(new IntegerInterval(0, 0));
        playerFilters7.setTeamSide(TeamSide.RED);
        playerFilters7.setWon(true);

        PlayerFilters playerFilters8 = new PlayerFilters();
        playerFilters8.setPuuid(
                "CFcq57-yO1Shwn5cVI0nkIHWIGgzl-QV1g5qMAliHHZuFo_HBoX-S6ZjhidAKLenTr5ziJAHXxw-aQ");
        playerFilters8.setPlayerName("Yoriichı");
        playerFilters8.setPlayerTag("EUW");
        playerFilters8.setSummonerIcon(5045);
        playerFilters8.setSummonerLevel(new IntegerInterval(210, 210));
        playerFilters8.setKills(new IntegerInterval(5, 5));
        playerFilters8.setDeaths(new IntegerInterval(1, 1));
        playerFilters8.setAssists(new IntegerInterval(5, 5));
        playerFilters8.setHealing(new IntegerInterval(6144, 6144));
        playerFilters8.setDamageToPlayers(new IntegerInterval(140039, 140039));
        playerFilters8.setWards(new IntegerInterval(5, 5));
        playerFilters8.setGold(new IntegerInterval(10871, 10871));
        playerFilters8.setRole(Role.MID);
        playerFilters8.setChampions(List.of(Champion.AHRI));
        playerFilters8.setCsPerMinute(new DoubleInterval(9.7, 9.7));
        playerFilters8.setKilledMinions(new IntegerInterval(221, 221));
        playerFilters8.setTripleKills(new IntegerInterval(0, 0));
        playerFilters8.setQuadKills(new IntegerInterval(0, 0));
        playerFilters8.setPentaKills(new IntegerInterval(0, 0));
        playerFilters8.setTeamSide(TeamSide.RED);
        playerFilters8.setWon(true);

        PlayerFilters playerFilters9 = new PlayerFilters();
        playerFilters9.setPuuid(
                "vyzwo-B74dAgHNsstNoWPioVU8bLh6kye-0xtdFTMQwM4Y-kCSZfwMpf29CadJJ1AvpYEPV5yNMicA");
        playerFilters9.setPlayerName("KC NEXT ADKING");
        playerFilters9.setPlayerTag("EUW");
        playerFilters9.setSummonerIcon(29);
        playerFilters9.setSummonerLevel(new IntegerInterval(727, 727));
        playerFilters9.setKills(new IntegerInterval(13, 13));
        playerFilters9.setDeaths(new IntegerInterval(3, 3));
        playerFilters9.setAssists(new IntegerInterval(12, 12));
        playerFilters9.setHealing(new IntegerInterval(12959, 12959));
        playerFilters9.setDamageToPlayers(new IntegerInterval(110972, 110972));
        playerFilters9.setWards(new IntegerInterval(10, 10));
        playerFilters9.setGold(new IntegerInterval(13924, 13924));
        playerFilters9.setRole(Role.BOTTOM);
        playerFilters9.setChampions(List.of(Champion.SENNA));
        playerFilters9.setCsPerMinute(new DoubleInterval(7.3, 7.3));
        playerFilters9.setKilledMinions(new IntegerInterval(165, 165));
        playerFilters9.setTripleKills(new IntegerInterval(0, 0));
        playerFilters9.setQuadKills(new IntegerInterval(0, 0));
        playerFilters9.setPentaKills(new IntegerInterval(0, 0));
        playerFilters9.setTeamSide(TeamSide.RED);
        playerFilters9.setWon(true);

        PlayerFilters playerFilters10 = new PlayerFilters();
        playerFilters10.setPuuid(
                "fwP-3K-Z_ghLAruQYlntd03YSdRCJeD7kaJQFujGJJpiwZ-ie9Rwkc9SmsiExqcnbnssXGmoAtyqHA");
        playerFilters10.setPlayerName("berebooo");
        playerFilters10.setPlayerTag("TR1");
        playerFilters10.setSummonerIcon(3158);
        playerFilters10.setSummonerLevel(new IntegerInterval(346, 346));
        playerFilters10.setKills(new IntegerInterval(1, 1));
        playerFilters10.setDeaths(new IntegerInterval(6, 6));
        playerFilters10.setAssists(new IntegerInterval(26, 26));
        playerFilters10.setHealing(new IntegerInterval(3544, 3544));
        playerFilters10.setDamageToPlayers(new IntegerInterval(15953, 15953));
        playerFilters10.setWards(new IntegerInterval(33, 33));
        playerFilters10.setGold(new IntegerInterval(8253, 8253));
        playerFilters10.setRole(Role.SUPPORT);
        playerFilters10.setChampions(List.of(Champion.BLITZCRANK));
        playerFilters10.setCsPerMinute(new DoubleInterval(0.7, 0.7));
        playerFilters10.setKilledMinions(new IntegerInterval(15, 15));
        playerFilters10.setTripleKills(new IntegerInterval(0, 0));
        playerFilters10.setQuadKills(new IntegerInterval(0, 0));
        playerFilters10.setPentaKills(new IntegerInterval(0, 0));
        playerFilters10.setTeamSide(TeamSide.RED);
        playerFilters10.setWon(true);

        TeamFilters teamFilters1 = new TeamFilters();
        teamFilters1.setTeamSide(TeamSide.BLUE);
        teamFilters1.setTeamDeaths(new IntegerInterval(13, 13));
        teamFilters1.setTeamDeaths(new IntegerInterval(35, 35));
        teamFilters1.setTeamAssists(new IntegerInterval(19, 19));
        teamFilters1.setTeamHealing(new IntegerInterval(16414, 16414));

        TeamFilters teamFilters2 = new TeamFilters();
        teamFilters2.setTeamSide(TeamSide.RED);
        teamFilters2.setTeamDeaths(new IntegerInterval(35, 35));
        teamFilters2.setTeamDeaths(new IntegerInterval(14, 14));
        teamFilters2.setTeamAssists(new IntegerInterval(54, 54));
        teamFilters2.setTeamHealing(new IntegerInterval(35354, 35354));

        filters.setPlayers(
                List.of(
                        playerFilters1,
                        playerFilters2,
                        playerFilters3,
                        playerFilters4,
                        playerFilters5,
                        playerFilters6,
                        playerFilters7,
                        playerFilters8,
                        playerFilters9,
                        playerFilters10));

        filters.setTeams(List.of(teamFilters1, teamFilters2));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO), page, 1, 1);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(53)
    public void setupMatch2() {
        when(detailFillingAdapterService.getMatch(MATCH_ID2)).thenReturn(matchDTO2);
        when(trainingAdapterService.sendMatchForAnalysis(MATCH_ID2)).thenReturn(true);
        assertEquals(
                matchDTO2,
                matchHistoryService.addMatchById(MATCH_ID2, UUID.fromString(QUEUE_ID2), mmrDeltas2));
        verify(detailFillingAdapterService).getMatch(MATCH_ID2);
        verify(trainingAdapterService).sendMatchForAnalysis(MATCH_ID2);
    }

    @Test
    @Order(54)
    public void testFilterSortMatches_ShouldReturnFirstMatch_WhenPageIs0AndSizeIs1() {
        final int page = 0;
        final int size = 1;

        MatchFilters filters = new MatchFilters();

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO), page, 2, 2);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(55)
    public void testFilterSortMatches_ShouldReturnSecondMatch_WhenPageIs1AndSizeIs1() {
        final int page = 1;
        final int size = 1;

        MatchFilters filters = new MatchFilters();

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO2), page, 2, 2);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(56)
    public void testFilterSortMatches_ShouldReturnBothMatches_WhenPageIs0AndSizeIs2() {
        final int page = 0;
        final int size = 2;

        MatchFilters filters = new MatchFilters();

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO, matchDTO2), page, 1, 2);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(57)
    public void testFilterSortMatches_ShouldReturnEmpty_WhenPageIs1AndSizeIs2() {
        final int page = 1;
        final int size = 2;

        MatchFilters filters = new MatchFilters();

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(), page, 1, 2);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(58)
    public void testFilterSortMatches_ShouldReturnMatch1Then2_WhenSortedByTimeAsc() {
        // Sort by time ASC

        final int page = 0;
        final int size = 20;

        MatchFilters filters = new MatchFilters();

        SortParams sortParams = new SortParams(SortColumn.TIME, SortDirection.ASC);

        filters.setSortParams(List.of(sortParams));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO, matchDTO2), page, 1, 2);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(59)
    public void testGetMatchesEndpoint_ShouldReturnMatch2Then1_WhenSortedByTimeDesc() {
        // Sort by time DESC

        final int page = 0;
        final int size = 20;

        MatchFilters filters = new MatchFilters();

        SortParams sortParams = new SortParams(SortColumn.TIME, SortDirection.DESC);

        filters.setSortParams(List.of(sortParams));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO2, matchDTO), page, 1, 2);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(60)
    public void setupMatch3() {
        when(detailFillingAdapterService.getMatch(MATCH_ID3)).thenReturn(matchDTO3);
        when(trainingAdapterService.sendMatchForAnalysis(MATCH_ID3)).thenReturn(true);
        assertEquals(
                matchDTO3,
                matchHistoryService.addMatchById(MATCH_ID3, UUID.fromString(QUEUE_ID), mmrDeltas));
        verify(detailFillingAdapterService).getMatch(MATCH_ID3);
        verify(trainingAdapterService).sendMatchForAnalysis(MATCH_ID3);
    }

    @Test
    @Order(61)
    public void
            testFilterSortMatches_ShouldReturnMatch3Then1_WhenFilterByPatchAndChampionsAndSortByBlueKillsAscThenRedHealingDesc() {
        // Filter by matches of path 16.5 where someone played as champion ASHE and
        // player Fentasm played as one of the following champions: VAYNE, DIANA or JAX.
        // Sort by blue team kills ASC and then by red team healing DESC.

        int page = 0;
        int size = 20;

        MatchFilters filters = new MatchFilters();
        filters.setPatch(new PatchInterval("16.5", "16.5"));
        filters.setChampions(List.of(Champion.ASHE));

        PlayerFilters playerFilters = new PlayerFilters();
        playerFilters.setPlayerName("Fentasm");
        playerFilters.setChampions(List.of(Champion.VAYNE, Champion.DIANA, Champion.JAX));

        SortParams sortParams1 = new SortParams(SortColumn.BLUE_KILLS, SortDirection.ASC);
        SortParams sortParams2 = new SortParams(SortColumn.RED_HEALING, SortDirection.DESC);

        filters.setPlayers(List.of(playerFilters));
        filters.setSortParams(List.of(sortParams1, sortParams2));

        PaginatedResponseDTO<MatchDTO> expectedResponse =
                new PaginatedResponseDTO<>(List.of(matchDTO3, matchDTO), page, 1, 2);

        assertEquals(expectedResponse, matchFilterSorterService.filterSortMatches(page, size, filters));
    }

    @Test
    @Order(62)
    public void cleanUp() {
        matchHistoryService.cleanUp();
    }
}
