package roundtrip.collection;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-thirty-two-bytes-long-1234"
})
@Testcontainers
class CollectionIntegrationTest {

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
        jdbcTemplate.execute("TRUNCATE TABLE collection_places, collections, user_social_accounts, users CASCADE");
        redisson.getKeys().flushdb();
    }

    // ──────────────────── GET /collections ────────────────────

    @Test
    void getCollections_afterSignUp_returnsDefaultCollection() throws Exception {
        String token = signIn();

        mockMvc.perform(get("/collections")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].name").value("저장한 플레이스"))
            .andExpect(jsonPath("$.items[0].is_default").value(true))
            .andExpect(jsonPath("$.items[0].place_count").value(0))
            .andExpect(jsonPath("$.items[0].visibility").value("private"));
    }

    @Test
    void getCollections_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/collections"))
            .andExpect(status().isUnauthorized());
    }

    // ──────────────────── POST /collections ────────────────────

    @Test
    void createCollection_validRequest_returns201() throws Exception {
        String token = signIn();
        var body = Map.of("name", "일본 여행", "icon", "🗾");

        mockMvc.perform(post("/collections")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.collection_id").isNotEmpty())
            .andExpect(jsonPath("$.name").value("일본 여행"))
            .andExpect(jsonPath("$.icon").value("🗾"))
            .andExpect(jsonPath("$.is_default").value(false))
            .andExpect(jsonPath("$.visibility").value("private"));
    }

    @Test
    void createCollection_missingName_returns400() throws Exception {
        String token = signIn();
        var body = Map.of("icon", "🗾");

        mockMvc.perform(post("/collections")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createCollection_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/collections")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isUnauthorized());
    }

    // ──────────────────── PATCH /collections/:id ────────────────────

    @Test
    void updateCollection_ownedCollection_returnsUpdated() throws Exception {
        String token = signIn();
        UUID collectionId = createCollection(token, "원본이름", null);

        var body = Map.of("name", "변경이름", "visibility", "public");

        mockMvc.perform(patch("/collections/" + collectionId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("변경이름"))
            .andExpect(jsonPath("$.visibility").value("public"));
    }

    @Test
    void updateCollection_notOwned_returns404() throws Exception {
        String token1 = signIn();
        UUID collectionId = createCollection(token1, "내컬렉션", null);

        when(verifierRegistry.verify(eq(SocialProvider.KAKAO), eq("other-token")))
            .thenReturn(new SocialIdentity(SocialProvider.KAKAO, "other-social", "other@example.com"));
        String token2 = signInWith("other-token");

        mockMvc.perform(patch("/collections/" + collectionId)
                .header("Authorization", "Bearer " + token2)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"침범\"}"))
            .andExpect(status().isNotFound());
    }

    // ──────────────────── DELETE /collections/:id ────────────────────

    @Test
    void deleteCollection_nonDefault_returns204() throws Exception {
        String token = signIn();
        UUID collectionId = createCollection(token, "삭제할컬렉션", null);

        mockMvc.perform(delete("/collections/" + collectionId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        // 기본 컬렉션만 남아있어야 함
        mockMvc.perform(get("/collections")
                .header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].is_default").value(true));
    }

    @Test
    void deleteCollection_defaultCollection_returns403() throws Exception {
        String token = signIn();
        UUID defaultId = getDefaultCollectionId(token);

        mockMvc.perform(delete("/collections/" + defaultId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void deleteCollection_nonExisting_returns404() throws Exception {
        String token = signIn();

        mockMvc.perform(delete("/collections/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    // ──────────────────── GET /collections/:id/places ────────────────────

    @Test
    void getCollectionPlaces_emptyCollection_returnsEmptyList() throws Exception {
        String token = signIn();
        UUID collectionId = createCollection(token, "빈컬렉션", null);

        mockMvc.perform(get("/collections/" + collectionId + "/places")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.collection_id").isNotEmpty())
            .andExpect(jsonPath("$.name").value("빈컬렉션"))
            .andExpect(jsonPath("$.places").isArray())
            .andExpect(jsonPath("$.places.length()").value(0));
    }

    // ──────────────────── POST /collections/:id/places ────────────────────

    @Test
    void addPlace_validPlace_returns201AndAppearsInList() throws Exception {
        String token = signIn();
        UUID collectionId = createCollection(token, "장소추가", null);
        UUID placeId = insertTestPlace("시부야 교차로");

        mockMvc.perform(post("/collections/" + collectionId + "/places")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"place_id\":\"" + placeId + "\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/collections/" + collectionId + "/places")
                .header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.places.length()").value(1))
            .andExpect(jsonPath("$.places[0].canonical_name").value("시부야 교차로"));
    }

    @Test
    void addPlace_placeNotFound_returns404() throws Exception {
        String token = signIn();
        UUID collectionId = createCollection(token, "테스트", null);

        mockMvc.perform(post("/collections/" + collectionId + "/places")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"place_id\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void addPlace_duplicate_returns409() throws Exception {
        String token = signIn();
        UUID collectionId = createCollection(token, "중복테스트", null);
        UUID placeId = insertTestPlace("중복장소");

        String body = "{\"place_id\":\"" + placeId + "\"}";

        mockMvc.perform(post("/collections/" + collectionId + "/places")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/collections/" + collectionId + "/places")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict());
    }

    // ──────────────────── DELETE /collections/:id/places/:placeId ────────────────────

    @Test
    void removePlace_existingPlace_returns204() throws Exception {
        String token = signIn();
        UUID collectionId = createCollection(token, "제거테스트", null);
        UUID placeId = insertTestPlace("제거할장소");

        mockMvc.perform(post("/collections/" + collectionId + "/places")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"place_id\":\"" + placeId + "\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(delete("/collections/" + collectionId + "/places/" + placeId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/collections/" + collectionId + "/places")
                .header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.places.length()").value(0));
    }

    // ──────────────────── GET /collections/:id/share ────────────────────

    @Test
    void getShareLink_returns200WithShareUrl() throws Exception {
        String token = signIn();
        UUID collectionId = createCollection(token, "공유테스트", null);

        mockMvc.perform(get("/collections/" + collectionId + "/share")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.share_url").isNotEmpty())
            .andExpect(jsonPath("$.visibility").value("private"));
    }

    @Test
    void getShareLink_calledTwice_returnsSameToken() throws Exception {
        String token = signIn();
        UUID collectionId = createCollection(token, "공유반복", null);

        MvcResult r1 = mockMvc.perform(get("/collections/" + collectionId + "/share")
                .header("Authorization", "Bearer " + token))
            .andReturn();
        MvcResult r2 = mockMvc.perform(get("/collections/" + collectionId + "/share")
                .header("Authorization", "Bearer " + token))
            .andReturn();

        @SuppressWarnings("unchecked")
        String url1 = (String) objectMapper.readValue(r1.getResponse().getContentAsString(), Map.class).get("share_url");
        @SuppressWarnings("unchecked")
        String url2 = (String) objectMapper.readValue(r2.getResponse().getContentAsString(), Map.class).get("share_url");

        assertThat(url1).isEqualTo(url2);
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

    @SuppressWarnings("unchecked")
    private UUID createCollection(String token, String name, String icon) throws Exception {
        var reqBody = icon != null ? Map.of("name", name, "icon", icon) : Map.of("name", name);
        MvcResult result = mockMvc.perform(post("/collections")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqBody)))
            .andExpect(status().isCreated())
            .andReturn();
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("collection_id"));
    }

    @SuppressWarnings("unchecked")
    private UUID getDefaultCollectionId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/collections")
                .header("Authorization", "Bearer " + token))
            .andReturn();
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        var items = (java.util.List<Map<String, Object>>) body.get("items");
        return UUID.fromString((String) items.stream()
            .filter(i -> (Boolean) i.get("is_default"))
            .findFirst().orElseThrow()
            .get("collection_id"));
    }

    private UUID insertTestPlace(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO places (id, canonical_name, latitude, longitude, category, country_code) " +
            "VALUES (?, ?, 35.659, 139.700, 'ATTRACTION', 'JP')",
            id, name
        );
        return id;
    }
}
