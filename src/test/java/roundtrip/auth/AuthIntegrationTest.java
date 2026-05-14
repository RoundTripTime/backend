package roundtrip.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import roundtrip.auth.domain.SocialIdentity;
import roundtrip.auth.infrastructure.social.SocialIdTokenVerifierRegistry;
import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;
import roundtrip.user.domain.entity.SocialProvider;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-thirty-two-bytes-long-1234"
})
@Testcontainers
class AuthIntegrationTest {

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
        when(verifierRegistry.verify(eq(SocialProvider.KAKAO), eq("invalid-token")))
            .thenThrow(new BusinessException(ErrorCode.INVALID_ID_TOKEN, "검증 실패"));
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("TRUNCATE TABLE user_follows, user_social_accounts, users CASCADE");
        redisson.getKeys().flushdb();
    }

    @Test
    void signIn_newUser_returns201_withIsNewUserTrue() throws Exception {
        var body = signInBody("kakao", "valid-token");

        mockMvc.perform(post("/auth/social")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("Accept-Language", "ko-KR"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andExpect(jsonPath("$.refresh_token").isNotEmpty())
            .andExpect(jsonPath("$.user.is_new_user").value(true))
            .andExpect(jsonPath("$.user.locale").value("ko-KR"))
            .andExpect(jsonPath("$.user.credit_balance").value(0));
    }

    @Test
    void signIn_secondCallSameSocialId_returnsExistingUser() throws Exception {
        var body = signInBody("kakao", "valid-token");
        mockMvc.perform(post("/auth/social")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body))).andExpect(status().isCreated());

        mockMvc.perform(post("/auth/social")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.user.is_new_user").value(false));
    }

    @Test
    void refresh_validToken_rotatesAndReturnsNewPair() throws Exception {
        Map<String, Object> signIn = postSignIn("valid-token");
        String refresh1 = (String) signIn.get("refresh_token");

        MvcResult resp = mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refresh_token", refresh1))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andExpect(jsonPath("$.refresh_token").isNotEmpty())
            .andReturn();

        Map<String, Object> rotated = readJson(resp);
        assertThat(rotated.get("refresh_token")).isNotEqualTo(refresh1);
    }

    @Test
    void refresh_reusedToken_returns401_andClearsAllJti() throws Exception {
        Map<String, Object> signIn = postSignIn("valid-token");
        String refresh1 = (String) signIn.get("refresh_token");
        var body = Map.of("refresh_token", refresh1);

        mockMvc.perform(post("/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body))).andExpect(status().isOk());

        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void invalidIdToken_returns401() throws Exception {
        var body = signInBody("kakao", "invalid-token");

        mockMvc.perform(post("/auth/social")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_ID_TOKEN"));
    }

    @Test
    void logout_invalidatesAllRefreshTokens() throws Exception {
        Map<String, Object> signIn = postSignIn("valid-token");
        String access = (String) signIn.get("access_token");
        String refresh = (String) signIn.get("refresh_token");

        mockMvc.perform(delete("/auth/session")
                .header("Authorization", "Bearer " + access))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refresh_token", refresh))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(delete("/auth/session"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private Map<String, Object> signInBody(String provider, String idToken) {
        return Map.of("provider", provider, "id_token", idToken);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postSignIn(String idToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/social")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(signInBody("kakao", idToken)))
            .header("Accept-Language", "ko-KR"))
            .andExpect(status().isCreated())
            .andReturn();
        return readJson(result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    }
}
