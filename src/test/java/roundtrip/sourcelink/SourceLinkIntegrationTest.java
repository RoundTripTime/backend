package roundtrip.sourcelink;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import roundtrip.sourcelink.infrastructure.external.FeatherlessAiClient;
import roundtrip.sourcelink.infrastructure.external.KakaoLocalClient;
import roundtrip.sourcelink.infrastructure.external.PlaceParseResult;
import roundtrip.sourcelink.infrastructure.external.SupadataMetadataResponse;
import roundtrip.sourcelink.infrastructure.external.SupadataExtractResponse;
import roundtrip.sourcelink.infrastructure.external.SupadataExtractResultResponse;
import roundtrip.sourcelink.infrastructure.external.SupadataClient;
import roundtrip.user.domain.entity.SocialProvider;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Testcontainers
class SourceLinkIntegrationTest {

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

    // 외부 API 클라이언트 Mock — 실제 네트워크 호출 차단
    @MockitoBean
    SupadataClient supadataClient;

    @MockitoBean
    FeatherlessAiClient featherlessAiClient;

    @MockitoBean
    KakaoLocalClient kakaoLocalClient;

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
            .thenReturn(new SocialIdentity(SocialProvider.KAKAO, "kakao-social-1", "u@example.com"));

        // ExtractionPipelineService는 @Async로 동작하는데,
        // supadataClient, geminiClient를 mock하므로 파이프라인이 실행되어도 안전함
        // 기본적으로 Gemini가 빈 결과를 반환하도록 설정 → job이 FAILED로 처리됨
        when(supadataClient.fetchMetadata(any()))
            .thenReturn(new SupadataMetadataResponse(
                "youtube", "video", "abc123",
                "https://youtube.com/shorts/abc123",
                "도쿄 숨은 맛집 VLOG",
                "시부야 맛집 추천",
                List.of("도쿄", "맛집")
            ));
        when(featherlessAiClient.parsePlaces(any()))
            .thenReturn(List.of());
        when(supadataClient.submitExtract(any(), any()))
            .thenReturn(new SupadataExtractResponse("mock-extract-job-id"));
        when(supadataClient.getExtractResult(any()))
            .thenReturn(new SupadataExtractResultResponse("failed", null, "test"));

        accessToken = signIn();
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("""
            TRUNCATE TABLE place_candidates, extraction_jobs, source_links,
                           user_follows, user_social_accounts, users CASCADE
            """);
    }

    // ─────────────────────────── POST /source-links ───────────────────────────

    @Test
    void submitLink_youtubeShort_returns201WithJobId() throws Exception {
        mockMvc.perform(post("/source-links")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("url", "https://youtube.com/shorts/abc123")
                )))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.source_link_id").isNotEmpty())
            .andExpect(jsonPath("$.job_id").isNotEmpty())
            .andExpect(jsonPath("$.job_status").value("pending"))
            .andExpect(jsonPath("$.source_type").value("youtube_short"))
            .andExpect(jsonPath("$.submitted_at").isNotEmpty());
    }

    @Test
    void submitLink_instagramReel_returns201() throws Exception {
        mockMvc.perform(post("/source-links")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("url", "https://www.instagram.com/reel/xyz789")
                )))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.source_type").value("instagram_reel"));
    }

    @Test
    void submitLink_unsupportedPlatform_returns422() throws Exception {
        mockMvc.perform(post("/source-links")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("url", "https://tiktok.com/@user/video/12345")
                )))
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_PLATFORM"));
    }

    @Test
    void submitLink_duplicateUrl_returns409() throws Exception {
        var body = objectMapper.writeValueAsString(
            Map.of("url", "https://youtube.com/shorts/dup123")
        );

        mockMvc.perform(post("/source-links")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/source-links")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("DUPLICATE_LINK"));
    }

    @Test
    void submitLink_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/source-links")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("url", "https://youtube.com/shorts/abc")
                )))
            .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────── GET /source-links ───────────────────────────

    @Test
    void listLinks_emptyForNewUser_returns200WithEmptyItems() throws Exception {
        mockMvc.perform(get("/source-links")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void listLinks_afterSubmit_containsSubmittedLink() throws Exception {
        // 제출
        mockMvc.perform(post("/source-links")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("url", "https://youtube.com/shorts/list-test")
                )))
            .andExpect(status().isCreated());

        // 목록 조회
        mockMvc.perform(get("/source-links")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].url").value("https://youtube.com/shorts/list-test"))
            .andExpect(jsonPath("$.items[0].source_type").value("youtube_short"))
            .andExpect(jsonPath("$.items[0].job_status").isNotEmpty());
    }

    // ─────────────────────────── GET /jobs/:job_id ───────────────────────────

    @Test
    void getJob_existingJob_returns200() throws Exception {
        MvcResult submitResult = mockMvc.perform(post("/source-links")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("url", "https://youtube.com/shorts/job-test")
                )))
            .andExpect(status().isCreated())
            .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> submitBody = objectMapper.readValue(
            submitResult.getResponse().getContentAsString(), Map.class);
        String jobId = (String) submitBody.get("job_id");

        mockMvc.perform(get("/jobs/" + jobId)
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.job_id").value(jobId))
            .andExpect(jsonPath("$.job_status").isNotEmpty())
            .andExpect(jsonPath("$.signal_count").value(0));
    }

    @Test
    void getJob_featherlessReturnsPlaces_eventuallyReturnsDone() throws Exception {
        when(featherlessAiClient.parsePlaces(any()))
            .thenReturn(List.of(new PlaceParseResult(
                "경복궁", "attraction", 0.95, "영상에서 경복궁을 소개함"
            )));

        MvcResult submitResult = mockMvc.perform(post("/source-links")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("url", "https://youtube.com/shorts/job-done-test")
                )))
            .andExpect(status().isCreated())
            .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> submitBody = objectMapper.readValue(
            submitResult.getResponse().getContentAsString(), Map.class);
        String jobId = (String) submitBody.get("job_id");

        for (int attempt = 0; attempt < 40; attempt++) {
            MvcResult jobResult = mockMvc.perform(get("/jobs/" + jobId)
                    .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
            @SuppressWarnings("unchecked")
            Map<String, Object> jobBody = objectMapper.readValue(
                jobResult.getResponse().getContentAsString(), Map.class);

            if ("done".equals(jobBody.get("job_status"))) {
                assertThat(jobBody.get("signal_count")).isEqualTo(1);
                assertThat(jobBody.get("error_code")).isNull();
                return;
            }
            Thread.sleep(50);
        }

        throw new AssertionError("Job did not reach done status");
    }

    @Test
    void getJob_nonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/jobs/00000000-0000-0000-0000-000000000000")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"));
    }

    // ─────────────────────── GET /jobs/:job_id/candidates ────────────────────

    @Test
    void getCandidates_jobWithNoCandidate_returnsEmptyList() throws Exception {
        MvcResult submitResult = mockMvc.perform(post("/source-links")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("url", "https://youtube.com/shorts/cand-test")
                )))
            .andExpect(status().isCreated())
            .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(
            submitResult.getResponse().getContentAsString(), Map.class);
        String jobId = (String) body.get("job_id");

        mockMvc.perform(get("/jobs/" + jobId + "/candidates")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.candidates").isArray())
            .andExpect(jsonPath("$.source_link").isNotEmpty());
    }

    // ────────────────────────── PATCH /candidates/:id ────────────────────────

    @Test
    void updateCandidate_nonExistentId_returns404() throws Exception {
        mockMvc.perform(patch("/candidates/00000000-0000-0000-0000-000000000000")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "accepted"))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("CANDIDATE_NOT_FOUND"));
    }

    // ─────────────────────── POST /candidates/batch ──────────────────────────

    @Test
    void batchUpdate_emptyList_returns400() throws Exception {
        mockMvc.perform(post("/candidates/batch")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("candidates", List.of()))))
            .andExpect(status().isBadRequest());
    }

    // ─────────────────────────── helpers ─────────────────────────────────────

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
