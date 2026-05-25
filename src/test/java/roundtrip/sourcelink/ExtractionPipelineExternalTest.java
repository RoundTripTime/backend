package roundtrip.sourcelink;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
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
import roundtrip.user.domain.entity.SocialProvider;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 외부 API(Supadata, Gemini)를 호출하는 파이프라인 통합 테스트.
 * .env 파일에 SUPADATA_API_KEY, GEMINI_API_KEY가 설정되어 있어야 합니다.
 *
 * 실행: ./gradlew test -Dgroups=external
 */
@Tag("external")
@SpringBootTest
@Testcontainers
class ExtractionPipelineExternalTest {

    // 12개 장소가 기대되는 쇼츠
    private static final String URL_12_PLACES = "https://www.youtube.com/shorts/8stXEkFjqts";
    // 7개 장소가 기대되는 쇼츠
    private static final String URL_7_PLACES  = "https://www.youtube.com/shorts/NlQqFDp1f_I";

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

    // SocialIdTokenVerifier만 Mock — 외부 API 클라이언트는 실제 빈 사용
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
            .thenReturn(new SocialIdentity(SocialProvider.KAKAO, "ext-test-social-1", "ext@example.com"));

        accessToken = signIn();
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("""
            TRUNCATE TABLE place_candidates, extraction_jobs, source_links,
                           user_follows, user_social_accounts, users CASCADE
            """);
    }

    @Test
    void pipeline_12placesShort_extractsExpectedCount() throws Exception {
        runPipelineAndAssertCandidateCount(URL_12_PLACES, 12);
    }

    @Test
    void pipeline_7placesShort_extractsExpectedCount() throws Exception {
        runPipelineAndAssertCandidateCount(URL_7_PLACES, 7);
    }

    private void runPipelineAndAssertCandidateCount(String url, int expectedCount) throws Exception {
        // 1. 링크 제출
        MvcResult submitResult = mockMvc.perform(post("/source-links")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("url", url))))
            .andExpect(status().isCreated())
            .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> submitBody = objectMapper.readValue(
            submitResult.getResponse().getContentAsString(), Map.class);

        String jobId = (String) submitBody.get("job_id");
        assertThat(jobId).isNotNull();

        // 2. 파이프라인 완료 대기 (최대 90초 — Supadata Extract 폴링 포함)
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(90).toMillis();
        String jobStatus = "pending";
        while (System.currentTimeMillis() < deadline) {
            MvcResult jobResult = mockMvc.perform(get("/jobs/" + jobId)
                    .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

            @SuppressWarnings("unchecked")
            Map<String, Object> jobBody = objectMapper.readValue(
                jobResult.getResponse().getContentAsString(), Map.class);

            jobStatus = (String) jobBody.get("job_status");
            if (!"pending".equals(jobStatus) && !"processing".equals(jobStatus)) {
                break;
            }
            Thread.sleep(3000);
        }

        assertThat(jobStatus)
            .as("파이프라인이 90초 내에 완료되어야 합니다 (url=%s)", url)
            .isEqualTo("done");

        // 3. candidates 수 검증
        MvcResult candResult = mockMvc.perform(get("/jobs/" + jobId + "/candidates")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> candBody = objectMapper.readValue(
            candResult.getResponse().getContentAsString(), Map.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) candBody.get("candidates");
        System.out.printf("[ExternalTest] url=%s, 추출 장소 수=%d (기대값=%d)%n",
            url, candidates.size(), expectedCount);
        candidates.forEach(c -> System.out.printf("  - [%s] %s (confidence=%.2f, requiresConfirmation=%s)%n",
            c.get("category"), c.get("candidate_name"), ((Number) c.get("confidence_score")).doubleValue(),
            c.get("requires_confirmation")));

        assertThat(candidates)
            .as("url=%s 에서 %d개 장소가 추출되어야 합니다", url, expectedCount)
            .hasSize(expectedCount);
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
