package roundtrip.notification;

import org.junit.jupiter.api.AfterEach;
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

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class NotificationIntegrationTest {

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
    String token;
    UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();

        when(verifierRegistry.verify(eq(SocialProvider.KAKAO), eq("valid-token")))
            .thenReturn(new SocialIdentity(SocialProvider.KAKAO, "kakao-social-1", "u@example.com"));

        token = signIn();
        userId = getUserId();
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("TRUNCATE TABLE notifications, user_social_accounts, users CASCADE");
        redisson.getKeys().flushdb();
    }

    // ──────────────────── GET /notifications ────────────────────

    @Test
    void getNotifications_empty_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/notifications")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(0))
            .andExpect(jsonPath("$.next_cursor").isEmpty());
    }

    @Test
    void getNotifications_withData_returnsList() throws Exception {
        insertNotification(userId, "job_completed", "장소 3곳을 찾았어요.", false);
        insertNotification(userId, "job_failed", "분석에 실패했습니다.", false);

        mockMvc.perform(get("/notifications")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void getNotifications_filterByIsRead_returnsFiltered() throws Exception {
        insertNotification(userId, "job_completed", "읽은 알림", true);
        insertNotification(userId, "job_failed", "안읽은 알림", false);

        mockMvc.perform(get("/notifications")
                .header("Authorization", "Bearer " + token)
                .param("is_read", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].message").value("안읽은 알림"));

        mockMvc.perform(get("/notifications")
                .header("Authorization", "Bearer " + token)
                .param("is_read", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].message").value("읽은 알림"));
    }

    @Test
    void getNotifications_pagination_works() throws Exception {
        for (int i = 0; i < 5; i++) {
            insertNotification(userId, "job_completed", "알림 " + i, false);
        }

        MvcResult r1 = mockMvc.perform(get("/notifications")
                .header("Authorization", "Bearer " + token)
                .param("limit", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.next_cursor").isNotEmpty())
            .andReturn();

        @SuppressWarnings("unchecked")
        String cursor = (String) objectMapper.readValue(
            r1.getResponse().getContentAsString(), Map.class).get("next_cursor");

        mockMvc.perform(get("/notifications")
                .header("Authorization", "Bearer " + token)
                .param("limit", "2")
                .param("cursor", cursor))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void getNotifications_otherUserNotifications_notVisible() throws Exception {
        // 다른 유저의 알림은 보이지 않음
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO users (id, nickname, locale, home_region) VALUES (?, '다른유저', 'ko-KR', '서울')",
            otherUserId);
        insertNotification(otherUserId, "job_completed", "다른유저 알림", false);

        mockMvc.perform(get("/notifications")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void getNotifications_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/notifications"))
            .andExpect(status().isUnauthorized());
    }

    // ──────────────────── PATCH /notifications/:id/read ────────────────────

    @Test
    void markAsRead_validNotification_returns200() throws Exception {
        UUID notifId = insertNotification(userId, "job_completed", "읽음처리 테스트", false);

        mockMvc.perform(patch("/notifications/" + notifId + "/read")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notification_id").value(notifId.toString()))
            .andExpect(jsonPath("$.is_read").value(true));

        // 읽음 상태 확인
        mockMvc.perform(get("/notifications")
                .header("Authorization", "Bearer " + token)
                .param("is_read", "true"))
            .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void markAsRead_alreadyRead_returns200() throws Exception {
        UUID notifId = insertNotification(userId, "job_completed", "이미 읽음", true);

        mockMvc.perform(patch("/notifications/" + notifId + "/read")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.is_read").value(true));
    }

    @Test
    void markAsRead_nonExisting_returns404() throws Exception {
        mockMvc.perform(patch("/notifications/" + UUID.randomUUID() + "/read")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void markAsRead_otherUserNotification_returns404() throws Exception {
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO users (id, nickname, locale, home_region) VALUES (?, '다른유저2', 'ko-KR', '서울')",
            otherUserId);
        UUID notifId = insertNotification(otherUserId, "job_completed", "타인 알림", false);

        mockMvc.perform(patch("/notifications/" + notifId + "/read")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void markAsRead_withoutToken_returns401() throws Exception {
        mockMvc.perform(patch("/notifications/" + UUID.randomUUID() + "/read"))
            .andExpect(status().isUnauthorized());
    }

    // ──────────────────── Response format ────────────────────

    @Test
    void getNotifications_responseFormat_matchesSpec() throws Exception {
        UUID jobId = UUID.randomUUID();
        insertNotificationWithJob(userId, "job_completed", jobId, "시부야 VLOG에서 장소 3곳을 찾았어요.", false);

        mockMvc.perform(get("/notifications")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].notification_id").isNotEmpty())
            .andExpect(jsonPath("$.items[0].type").value("job_completed"))
            .andExpect(jsonPath("$.items[0].job_id").value(jobId.toString()))
            .andExpect(jsonPath("$.items[0].message").value("시부야 VLOG에서 장소 3곳을 찾았어요."))
            .andExpect(jsonPath("$.items[0].is_read").value(false))
            .andExpect(jsonPath("$.items[0].created_at").isNotEmpty());
    }

    // ──────────────────── helpers ────────────────────

    @SuppressWarnings("unchecked")
    private String signIn() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/social")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("provider", "kakao", "id_token", "valid-token")))
                .header("Accept-Language", "ko-KR"))
            .andExpect(status().isCreated())
            .andReturn();
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) body.get("access_token");
    }

    @SuppressWarnings("unchecked")
    private UUID getUserId() throws Exception {
        MvcResult result = mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + token))
            .andReturn();
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }

    private UUID insertNotification(UUID userId, String type, String message, boolean isRead) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO notifications (id, user_id, type, message, is_read) VALUES (?, ?, ?, ?, ?)",
            id, userId, type, message, isRead
        );
        return id;
    }

    private UUID insertNotificationWithJob(UUID userId, String type, UUID jobId, String message, boolean isRead) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO notifications (id, user_id, type, job_id, message, is_read) VALUES (?, ?, ?, ?, ?, ?)",
            id, userId, type, jobId, message, isRead
        );
        return id;
    }
}
