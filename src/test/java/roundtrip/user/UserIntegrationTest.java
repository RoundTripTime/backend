package roundtrip.user;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
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
import roundtrip.common.exception.BusinessException;
import roundtrip.user.domain.entity.SocialProvider;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-thirty-two-bytes-long-1234"
})
@Testcontainers
class UserIntegrationTest {

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
            .thenThrow(new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_ID_TOKEN", "검증 실패"));
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("TRUNCATE TABLE user_follows, user_social_accounts, users CASCADE");
        redisson.getKeys().flushdb();
    }

    @Test
    void getMyProfile_authenticated_returnsAllFieldsInSnakeCase() throws Exception {
        String token = signInAndGetAccessToken();

        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.nickname").isNotEmpty())
            .andExpect(jsonPath("$.email").value("u@example.com"))
            .andExpect(jsonPath("$.locale").value("ko-KR"))
            .andExpect(jsonPath("$.home_region").value("Unknown"))
            .andExpect(jsonPath("$.map_provider").value("kakao"))
            .andExpect(jsonPath("$.credit_balance").value(0))
            .andExpect(jsonPath("$.created_at").isNotEmpty());
    }

    @Test
    void getMyProfile_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void updateProfile_partialFields_persistsOnlyChanges() throws Exception {
        String token = signInAndGetAccessToken();

        Map<String, Object> body = new HashMap<>();
        body.put("nickname", "새닉네임");

        mockMvc.perform(patch("/users/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nickname").value("새닉네임"))
            .andExpect(jsonPath("$.locale").value("ko-KR"))
            .andExpect(jsonPath("$.home_region").value("Unknown"))
            .andExpect(jsonPath("$.map_provider").value("kakao"));

        mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.nickname").value("새닉네임"));
    }

    @Test
    void updateProfile_allFields_persistsAll() throws Exception {
        String token = signInAndGetAccessToken();

        Map<String, Object> body = Map.of(
            "nickname", "전체변경",
            "avatar_url", "https://cdn.example.com/avatar.png",
            "home_region", "Tokyo",
            "locale", "en-US",
            "map_provider", "google"
        );

        mockMvc.perform(patch("/users/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nickname").value("전체변경"))
            .andExpect(jsonPath("$.avatar_url").value("https://cdn.example.com/avatar.png"))
            .andExpect(jsonPath("$.home_region").value("Tokyo"))
            .andExpect(jsonPath("$.locale").value("en-US"))
            .andExpect(jsonPath("$.map_provider").value("google"));
    }

    @Test
    void updateProfile_invalidNicknameLength_returns400() throws Exception {
        String token = signInAndGetAccessToken();
        Map<String, Object> body = Map.of("nickname", "a".repeat(21));

        mockMvc.perform(patch("/users/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateProfile_invalidMapProvider_returns400() throws Exception {
        String token = signInAndGetAccessToken();
        Map<String, Object> body = Map.of("map_provider", "naver");

        mockMvc.perform(patch("/users/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateProfile_withoutToken_returns401() throws Exception {
        mockMvc.perform(patch("/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"x\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteMyProfile_authenticated_returns204_andCascadesSocialAccounts() throws Exception {
        String token = signInAndGetAccessToken();

        Integer usersBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        Integer socialsBefore = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_social_accounts", Integer.class);
        assertThat(usersBefore).isEqualTo(1);
        assertThat(socialsBefore).isEqualTo(1);

        mockMvc.perform(delete("/users/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        Integer usersAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        Integer socialsAfter = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_social_accounts", Integer.class);
        assertThat(usersAfter).isZero();
        assertThat(socialsAfter).isZero();
    }

    @Test
    void deleteMyProfile_withoutToken_returns401() throws Exception {
        mockMvc.perform(delete("/users/me"))
            .andExpect(status().isUnauthorized());
    }

    private String signInAndGetAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/social")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("provider", "kakao", "id_token", "valid-token")))
                .header("Accept-Language", "ko-KR"))
            .andExpect(status().isCreated())
            .andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(
            result.getResponse().getContentAsString(), Map.class);
        return (String) body.get("access_token");
    }
}
