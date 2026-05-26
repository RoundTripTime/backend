package roundtrip.credit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
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

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class CreditIntegrationTest {

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
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @MockitoBean
    SocialIdTokenVerifierRegistry verifierRegistry;

    @Autowired WebApplicationContext context;
    @Autowired JsonMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired RedissonClient redisson;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();

        when(verifierRegistry.verify(eq(SocialProvider.KAKAO), eq("valid-token")))
            .thenReturn(new SocialIdentity(SocialProvider.KAKAO, "kakao-social-1", "u@example.com"));
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("TRUNCATE TABLE ad_sessions, credit_histories, user_social_accounts, users CASCADE");
        redisson.getKeys().flushdb();
    }

    // ──────────────────── GET /credits/me ────────────────────

    @Test
    void getMyCredit_noHistory_returnsZeros() throws Exception {
        String token = signIn();

        mockMvc.perform(get("/credits/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance").value(0))
            .andExpect(jsonPath("$.lifetime_earned").value(0))
            .andExpect(jsonPath("$.lifetime_spent").value(0));
    }

    @Test
    void getMyCredit_withHistory_returnsBalanceAndLifetimeSums() throws Exception {
        String token = signIn();
        UUID userId = currentUserId();
        OffsetDateTime base = OffsetDateTime.parse("2024-12-01T10:00:00Z");
        insertHistory(userId, "ad_view", 1, 1, "광고 시청 보상", base);
        insertHistory(userId, "ota_booking", 1, 2, "OTA 예약 적립", base.plusMinutes(1));
        insertHistory(userId, "plan_purchase", -1, 1, "플랜 열람", base.plusMinutes(2));
        jdbcTemplate.update("UPDATE users SET credit_balance = 1");

        mockMvc.perform(get("/credits/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance").value(1))
            .andExpect(jsonPath("$.lifetime_earned").value(2))
            .andExpect(jsonPath("$.lifetime_spent").value(1));
    }

    @Test
    void getMyCredit_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/credits/me"))
            .andExpect(status().isUnauthorized());
    }

    // ──────────────────── GET /credits/me/history ────────────────────

    @Test
    void getHistory_empty_returnsEmptyListAndNullCursor() throws Exception {
        String token = signIn();

        mockMvc.perform(get("/credits/me/history")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(0))
            .andExpect(jsonPath("$.next_cursor").doesNotExist());
    }

    @Test
    void getHistory_returnsNewestFirst() throws Exception {
        String token = signIn();
        UUID userId = currentUserId();
        OffsetDateTime base = OffsetDateTime.parse("2024-12-01T10:00:00Z");
        insertHistory(userId, "ad_view", 1, 1, "오래된 적립", base);
        insertHistory(userId, "plan_purchase", -1, 0, "최근 차감", base.plusHours(1));

        mockMvc.perform(get("/credits/me/history")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].credit_type").value("plan_purchase"))
            .andExpect(jsonPath("$.items[0].amount").value(-1))
            .andExpect(jsonPath("$.items[1].credit_type").value("ad_view"));
    }

    @Test
    void getHistory_typeEarned_returnsOnlyPositive() throws Exception {
        String token = signIn();
        UUID userId = currentUserId();
        OffsetDateTime base = OffsetDateTime.parse("2024-12-01T10:00:00Z");
        insertHistory(userId, "ad_view", 1, 1, "적립", base);
        insertHistory(userId, "plan_purchase", -1, 0, "차감", base.plusMinutes(1));

        mockMvc.perform(get("/credits/me/history")
                .param("type", "earned")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].amount").value(1));
    }

    @Test
    void getHistory_typeSpent_returnsOnlyNegative() throws Exception {
        String token = signIn();
        UUID userId = currentUserId();
        OffsetDateTime base = OffsetDateTime.parse("2024-12-01T10:00:00Z");
        insertHistory(userId, "ad_view", 1, 1, "적립", base);
        insertHistory(userId, "plan_purchase", -1, 0, "차감", base.plusMinutes(1));

        mockMvc.perform(get("/credits/me/history")
                .param("type", "spent")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].amount").value(-1));
    }

    @Test
    void getHistory_pagination_walksWithCursor() throws Exception {
        String token = signIn();
        UUID userId = currentUserId();
        OffsetDateTime base = OffsetDateTime.parse("2024-12-01T10:00:00Z");
        insertHistory(userId, "ad_view", 1, 1, "1번(가장 오래됨)", base);
        insertHistory(userId, "ad_view", 1, 2, "2번", base.plusMinutes(1));
        insertHistory(userId, "ad_view", 1, 3, "3번(가장 최근)", base.plusMinutes(2));

        MvcResult page1 = mockMvc.perform(get("/credits/me/history")
                .param("limit", "2")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].description").value("3번(가장 최근)"))
            .andExpect(jsonPath("$.items[1].description").value("2번"))
            .andExpect(jsonPath("$.next_cursor").isNotEmpty())
            .andReturn();

        String cursor = readNextCursor(page1);

        mockMvc.perform(get("/credits/me/history")
                .param("limit", "2")
                .param("cursor", cursor)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].description").value("1번(가장 오래됨)"))
            .andExpect(jsonPath("$.next_cursor").doesNotExist());
    }

    @Test
    void getHistory_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/credits/me/history"))
            .andExpect(status().isUnauthorized());
    }

    // ──────────────────── POST /credits/ads/start ────────────────────

    @Test
    void startAd_firstTime_returnsViewedTodayZero() throws Exception {
        String token = signIn();

        mockMvc.perform(post("/credits/ads/start")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.ad_session_id").isNotEmpty())
            .andExpect(jsonPath("$.expires_at").isNotEmpty())
            .andExpect(jsonPath("$.viewed_today").value(0))
            .andExpect(jsonPath("$.required_for_credit").value(5));
    }

    @Test
    void startAd_dailyLimitReached_returns422() throws Exception {
        String token = signIn();
        UUID userId = currentUserId();
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 0; i < 20; i++) {
            insertCompletedAdSession(userId, now.minusMinutes(i));
        }

        mockMvc.perform(post("/credits/ads/start")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("AD_LIMIT_REACHED"));
    }

    @Test
    void startAd_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/credits/ads/start"))
            .andExpect(status().isUnauthorized());
    }

    // ──────────────────── POST /credits/ads/complete ────────────────────

    @Test
    void completeAd_firstView_returnsCreditEarnedFalse() throws Exception {
        String token = signIn();
        UUID sessionId = startAdAndGetSessionId(token);

        mockMvc.perform(post("/credits/ads/complete")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ad_session_id", sessionId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.viewed_today").value(1))
            .andExpect(jsonPath("$.required_for_credit").value(5))
            .andExpect(jsonPath("$.credit_earned").value(false))
            .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void completeAd_fifthView_earnsOneCredit() throws Exception {
        String token = signIn();
        UUID userId = currentUserId();
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 0; i < 4; i++) {
            insertCompletedAdSession(userId, now.minusMinutes(i));
        }
        UUID sessionId = startAdAndGetSessionId(token);

        mockMvc.perform(post("/credits/ads/complete")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ad_session_id", sessionId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.viewed_today").value(5))
            .andExpect(jsonPath("$.credit_earned").value(true))
            .andExpect(jsonPath("$.balance").value(1));

        Integer balance = jdbcTemplate.queryForObject(
            "SELECT credit_balance FROM users WHERE id = ?", Integer.class, userId);
        Assertions.assertEquals(1, balance);

        Long historyCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM credit_histories WHERE user_id = ? AND credit_type = 'ad_view'",
            Long.class, userId);
        Assertions.assertEquals(1L, historyCount);
    }

    @Test
    void completeAd_alreadyCompleted_returns409() throws Exception {
        String token = signIn();
        UUID sessionId = startAdAndGetSessionId(token);
        String body = objectMapper.writeValueAsString(Map.of("ad_session_id", sessionId));

        mockMvc.perform(post("/credits/ads/complete")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());

        mockMvc.perform(post("/credits/ads/complete")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("AD_ALREADY_COMPLETED"));
    }

    @Test
    void completeAd_expiredSession_returns422() throws Exception {
        String token = signIn();
        UUID userId = currentUserId();
        UUID sessionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO ad_sessions (id, user_id, expires_at, is_completed, created_at) " +
            "VALUES (?, ?, ?, false, ?)",
            sessionId, userId, OffsetDateTime.now().minusMinutes(10), OffsetDateTime.now().minusMinutes(15)
        );

        mockMvc.perform(post("/credits/ads/complete")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ad_session_id", sessionId))))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("INVALID_AD_SESSION"));
    }

    @Test
    void completeAd_nonexistentSession_returns404() throws Exception {
        String token = signIn();

        mockMvc.perform(post("/credits/ads/complete")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ad_session_id", UUID.randomUUID()))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("AD_SESSION_NOT_FOUND"));
    }

    @Test
    void completeAd_otherUsersSession_returns404() throws Exception {
        String token = signIn();
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO users (id, nickname, locale, home_region, map_provider, credit_balance) " +
            "VALUES (?, '다른유저', 'ko-KR', 'KR', 'KAKAO', 0)",
            otherUserId
        );
        UUID sessionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO ad_sessions (id, user_id, expires_at, is_completed, created_at) " +
            "VALUES (?, ?, ?, false, NOW())",
            sessionId, otherUserId, OffsetDateTime.now().plusMinutes(5)
        );

        mockMvc.perform(post("/credits/ads/complete")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ad_session_id", sessionId))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("AD_SESSION_NOT_FOUND"));
    }

    @Test
    void completeAd_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/credits/ads/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ad_session_id", UUID.randomUUID()))))
            .andExpect(status().isUnauthorized());
    }

    // ──────────────────── helpers ────────────────────

    private String signIn() throws Exception {
        return signInWith("valid-token");
    }

    @SuppressWarnings("unchecked")
    private String signInWith(String idToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/social")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("provider", "kakao", "id_token", idToken)))
                .header("Accept-Language", "ko-KR"))
            .andExpect(status().isCreated())
            .andReturn();
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) body.get("access_token");
    }

    private UUID currentUserId() {
        return jdbcTemplate.queryForObject("SELECT id FROM users", UUID.class);
    }

    private void insertHistory(UUID userId, String creditType, int amount, int balanceAfter,
                               String description, OffsetDateTime createdAt) {
        jdbcTemplate.update(
            "INSERT INTO credit_histories (id, user_id, credit_type, amount, balance_after, description, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), userId, creditType, amount, balanceAfter, description, createdAt
        );
    }

    private void insertCompletedAdSession(UUID userId, OffsetDateTime createdAt) {
        jdbcTemplate.update(
            "INSERT INTO ad_sessions (id, user_id, expires_at, is_completed, created_at) " +
            "VALUES (?, ?, ?, true, ?)",
            UUID.randomUUID(), userId, createdAt.plusMinutes(5), createdAt
        );
    }

    @SuppressWarnings("unchecked")
    private UUID startAdAndGetSessionId(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/credits/ads/start")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andReturn();
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("ad_session_id"));
    }

    @SuppressWarnings("unchecked")
    private String readNextCursor(MvcResult result) throws Exception {
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) body.get("next_cursor");
    }
}
