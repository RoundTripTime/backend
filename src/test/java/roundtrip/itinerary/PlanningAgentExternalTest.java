package roundtrip.itinerary;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import roundtrip.auth.domain.SocialIdentity;
import roundtrip.auth.infrastructure.social.SocialIdTokenVerifierRegistry;
import roundtrip.user.domain.entity.SocialProvider;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 FeatherlessAI API를 호출하는 Planning Agent 통합 테스트.
 * .env 파일에 FEATHERLESSAI_API_KEY가 설정되어 있어야 합니다.
 *
 * 실행: ./gradlew externalTest
 */
@Tag("external")
@SpringBootTest
@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
class PlanningAgentExternalTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("roundtrip-postgres-test:latest")
            .asCompatibleSubstituteFor("postgres")
    ).withDatabaseName("roundtrip_test")
     .withUsername("test")
     .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void dynamicProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @MockitoBean
    SocialIdTokenVerifierRegistry verifierRegistry;

    @Autowired WebApplicationContext context;
    @Autowired JsonMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    MockMvc mockMvc;
    String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();

        when(verifierRegistry.verify(eq(SocialProvider.KAKAO), eq("valid-token")))
            .thenReturn(new SocialIdentity(SocialProvider.KAKAO, "agent-test-social-1", "agent@example.com"));

        accessToken = signIn();
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("""
            TRUNCATE TABLE itinerary_items, itineraries, places,
                           user_follows, user_social_accounts, users CASCADE
            """);
    }

    @Test
    void agent_chatAndFollowUp_returnsNaturalLanguageReply() throws Exception {
        UUID itineraryId = createItinerary();
        UUID placeId = insertTestPlace("해운대해수욕장");
        addItem(itineraryId, placeId, 1, 1);

        // 1차 요청: 일정 정보 질문
        var body1 = Map.of("message", "일정에 뭐가 있어?");

        MvcResult result1 = mockMvc.perform(post("/itineraries/" + itineraryId + "/agent")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply").isNotEmpty())
            .andExpect(jsonPath("$.itinerary_updated").isBoolean())
            .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> response1 = objectMapper.readValue(
            result1.getResponse().getContentAsString(), Map.class);

        String reply1 = (String) response1.get("reply");
        System.out.println("[AgentExternalTest] 1차 응답:");
        System.out.println("  reply: " + reply1);
        System.out.println("  tool_results: " + response1.get("tool_results"));
        System.out.println("  itinerary_updated: " + response1.get("itinerary_updated"));

        assertThat(reply1).as("Agent가 자연어로 일정 정보를 응답해야 합니다").isNotBlank();

        // 2차 요청: history 포함 후속 질문
        var body2 = Map.of(
            "message", "좋아, 고마워!",
            "history", List.of(
                Map.of("role", "user", "content", "일정에 뭐가 있어?"),
                Map.of("role", "assistant", "content", reply1)
            )
        );

        MvcResult result2 = mockMvc.perform(post("/itineraries/" + itineraryId + "/agent")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body2)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply").isNotEmpty())
            .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> response2 = objectMapper.readValue(
            result2.getResponse().getContentAsString(), Map.class);

        System.out.println("[AgentExternalTest] 2차 응답 (history context):");
        System.out.println("  reply: " + response2.get("reply"));

        assertThat((String) response2.get("reply")).isNotBlank();
    }

    // ──────────────── helpers ────────────────

    @SuppressWarnings("unchecked")
    private UUID createItinerary() throws Exception {
        var reqBody = Map.of(
            "title", "부산 2박 3일",
            "destination_region", "부산, 한국",
            "start_date", "2025-01-10",
            "end_date", "2025-01-12",
            "party_size", 2
        );
        MvcResult result = mockMvc.perform(post("/itineraries")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqBody)))
            .andExpect(status().isCreated())
            .andReturn();
        Map<String, Object> body = objectMapper.readValue(
            result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("itinerary_id"));
    }

    private void addItem(UUID itineraryId, UUID placeId, int dayIndex, int sortOrder) throws Exception {
        var reqBody = Map.of(
            "place_id", placeId.toString(),
            "day_index", dayIndex,
            "sort_order", sortOrder
        );
        mockMvc.perform(post("/itineraries/" + itineraryId + "/items")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqBody)))
            .andExpect(status().isCreated());
    }

    private UUID insertTestPlace(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO places (id, canonical_name, latitude, longitude, category, country_code) " +
            "VALUES (?, ?, 35.10065, 129.12449, 'ATTRACTION', 'KR')",
            id, name
        );
        return id;
    }

    private String signIn() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/social")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("provider", "kakao", "id_token", "valid-token")
                ))
                .header("Accept-Language", "ko-KR"))
            .andExpect(status().isCreated())
            .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(
            result.getResponse().getContentAsString(), Map.class);
        return (String) body.get("access_token");
    }
}
