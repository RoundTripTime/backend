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
import roundtrip.notification.application.DeviceTokenService;
import roundtrip.notification.application.NotificationService;
import roundtrip.notification.application.ReviewReminderTarget;
import roundtrip.notification.domain.entity.NotificationType;
import roundtrip.notification.domain.repository.DeviceTokenRepository;
import roundtrip.notification.domain.repository.ReviewReminderRepository;
import roundtrip.user.domain.entity.SocialProvider;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class NotificationCreationIntegrationTest {

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
    @Autowired DeviceTokenService deviceTokenService;
    @Autowired DeviceTokenRepository deviceTokenRepository;
    @Autowired NotificationService notificationService;
    @Autowired ReviewReminderRepository reviewReminderRepository;

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
        jdbcTemplate.execute("TRUNCATE TABLE notifications, device_tokens, place_candidates, " +
            "extraction_jobs, source_links, user_social_accounts, users CASCADE");
        redisson.getKeys().flushdb();
    }

    // ──────────────────── 디바이스 토큰 등록/해제 API ────────────────────

    @Test
    void registerDeviceToken_returns201AndPersists() throws Exception {
        mockMvc.perform(post("/notifications/device-tokens")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("token", "fcm-token-1", "platform", "android"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.device_token_id").isNotEmpty());

        assertThat(deviceTokenRepository.findByUserId(userId)).hasSize(1);
    }

    @Test
    void registerDeviceToken_sameToken_upsertsWithoutDuplicate() throws Exception {
        register("fcm-token-1", "android");
        register("fcm-token-1", "ios");

        List<roundtrip.notification.domain.entity.DeviceToken> tokens =
            deviceTokenRepository.findByUserId(userId);
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getPlatform()).isEqualTo("ios");
    }

    @Test
    void registerDeviceToken_invalidPlatform_returns400() throws Exception {
        mockMvc.perform(post("/notifications/device-tokens")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("token", "fcm-token-1", "platform", "desktop"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unregisterDeviceToken_removesOwnToken() throws Exception {
        register("fcm-token-1", "android");

        mockMvc.perform(delete("/notifications/device-tokens")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", "fcm-token-1"))))
            .andExpect(status().isNoContent());

        assertThat(deviceTokenRepository.findByUserId(userId)).isEmpty();
    }

    @Test
    void unregisterDeviceToken_otherUsersToken_isNotRemoved() {
        UUID otherUserId = insertUser("다른유저");
        deviceTokenService.register(otherUserId, "other-token", "android");

        deviceTokenService.unregister(userId, "other-token");

        assertThat(deviceTokenRepository.findByToken("other-token")).isPresent();
    }

    @Test
    void deviceTokenEndpoints_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/notifications/device-tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("token", "fcm-token-1", "platform", "android"))))
            .andExpect(status().isUnauthorized());
    }

    // ──────────────────── 검토 리마인드 조회/생성 ────────────────────

    @Test
    void reviewReminder_doneJobWithProposedCandidate_isTarget() {
        UUID jobId = insertJobWithCandidate(userId, "DONE",
            OffsetDateTime.now().minusMinutes(40), "PROPOSED");

        List<ReviewReminderTarget> targets =
            reviewReminderRepository.findJobsNeedingReviewReminder(OffsetDateTime.now().minusMinutes(30));

        assertThat(targets).extracting(ReviewReminderTarget::jobId).contains(jobId);
        assertThat(targets).extracting(ReviewReminderTarget::userId).contains(userId);
    }

    @Test
    void reviewReminder_recentJob_isNotTarget() {
        insertJobWithCandidate(userId, "DONE", OffsetDateTime.now().minusMinutes(5), "PROPOSED");

        List<ReviewReminderTarget> targets =
            reviewReminderRepository.findJobsNeedingReviewReminder(OffsetDateTime.now().minusMinutes(30));

        assertThat(targets).isEmpty();
    }

    @Test
    void reviewReminder_noProposedCandidate_isNotTarget() {
        insertJobWithCandidate(userId, "DONE", OffsetDateTime.now().minusMinutes(40), "ACCEPTED");

        List<ReviewReminderTarget> targets =
            reviewReminderRepository.findJobsNeedingReviewReminder(OffsetDateTime.now().minusMinutes(30));

        assertThat(targets).isEmpty();
    }

    @Test
    void reviewReminder_alreadySent_isNotTarget() {
        UUID jobId = insertJobWithCandidate(userId, "DONE",
            OffsetDateTime.now().minusMinutes(40), "PROPOSED");
        insertReminderNotification(userId, jobId);

        List<ReviewReminderTarget> targets =
            reviewReminderRepository.findJobsNeedingReviewReminder(OffsetDateTime.now().minusMinutes(30));

        assertThat(targets).extracting(ReviewReminderTarget::jobId).doesNotContain(jobId);
    }

    @Test
    void createReviewReminder_persistsNotificationWithReminderType() {
        UUID jobId = UUID.randomUUID();

        notificationService.createReviewReminder(userId, jobId);

        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT type, message, job_id, is_read FROM notifications WHERE user_id = ?", userId);
        assertThat(row.get("type")).isEqualTo(NotificationType.EXTRACTION_REVIEW_REMINDER.value());
        assertThat(row.get("message")).isEqualTo("추출한 장소를 확인해주세요!");
        assertThat(row.get("job_id")).isEqualTo(jobId);
        assertThat(row.get("is_read")).isEqualTo(false);
    }

    // ──────────────────── helpers ────────────────────

    private void register(String fcmToken, String platform) throws Exception {
        mockMvc.perform(post("/notifications/device-tokens")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("token", fcmToken, "platform", platform))))
            .andExpect(status().isCreated());
    }

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

    private UUID insertUser(String nickname) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO users (id, nickname, locale, home_region) VALUES (?, ?, 'ko-KR', '서울')",
            id, nickname);
        return id;
    }

    private UUID insertJobWithCandidate(UUID ownerId, String jobStatus,
                                        OffsetDateTime completedAt, String candidateStatus) {
        UUID sourceLinkId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO source_links (id, user_id, url, normalized_url_hash, status, visibility) " +
                "VALUES (?, ?, 'https://example.com/v', ?, 'DONE', 'private')",
            sourceLinkId, ownerId, UUID.randomUUID().toString());

        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO extraction_jobs (id, source_link_id, job_status, completed_at) VALUES (?, ?, ?, ?)",
            jobId, sourceLinkId, jobStatus, completedAt);

        jdbcTemplate.update(
            "INSERT INTO place_candidates (id, job_id, candidate_name, status) VALUES (?, ?, '카페', ?)",
            UUID.randomUUID(), jobId, candidateStatus);
        return jobId;
    }

    private void insertReminderNotification(UUID ownerId, UUID jobId) {
        jdbcTemplate.update(
            "INSERT INTO notifications (id, user_id, type, job_id, message, is_read) " +
                "VALUES (?, ?, ?, ?, '추출한 장소를 확인해주세요!', false)",
            UUID.randomUUID(), ownerId, NotificationType.EXTRACTION_REVIEW_REMINDER.value(), jobId);
    }
}
