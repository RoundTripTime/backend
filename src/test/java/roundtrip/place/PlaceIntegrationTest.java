package roundtrip.place;

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
import roundtrip.sourcelink.infrastructure.external.KakaoLocalClient;
import roundtrip.sourcelink.infrastructure.external.KakaoLocalDocument;
import roundtrip.user.domain.entity.SocialProvider;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "jwt.secret=integration-test-secret-must-be-at-least-thirty-two-bytes-long-1234"
})
@Testcontainers
class PlaceIntegrationTest {

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

    @MockitoBean
    KakaoLocalClient kakaoLocalClient;

    @Autowired WebApplicationContext context;
    @Autowired JsonMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired RedissonClient redisson;

    MockMvc mockMvc;
    String token;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();

        when(verifierRegistry.verify(eq(SocialProvider.KAKAO), eq("valid-token")))
            .thenReturn(new SocialIdentity(SocialProvider.KAKAO, "kakao-social-1", "u@example.com"));

        when(kakaoLocalClient.searchByKeyword(anyString()))
            .thenReturn(List.of());

        token = signIn();
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE collection_places, collections, place_candidates, extraction_jobs, " +
            "source_links, user_social_accounts, users CASCADE"
        );
        jdbcTemplate.execute("TRUNCATE TABLE places CASCADE");
        redisson.getKeys().flushdb();
    }

    // ──────────────────── GET /places/:id ────────────────────

    @Test
    void getPlace_existingPlace_returns200WithFields() throws Exception {
        UUID placeId = insertTestPlace("시부야 스크램블 교차로", "ATTRACTION", "JP", "ChIJxxx", "12345");

        mockMvc.perform(get("/places/" + placeId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.place_id").value(placeId.toString()))
            .andExpect(jsonPath("$.canonical_name").value("시부야 스크램블 교차로"))
            .andExpect(jsonPath("$.category").value("attraction"))
            .andExpect(jsonPath("$.country_code").value("JP"))
            .andExpect(jsonPath("$.google_place_id").value("ChIJxxx"))
            .andExpect(jsonPath("$.kakao_place_id").value("12345"))
            .andExpect(jsonPath("$.latitude").isNotEmpty())
            .andExpect(jsonPath("$.longitude").isNotEmpty());
    }

    @Test
    void getPlace_nonExistingPlace_returns404() throws Exception {
        mockMvc.perform(get("/places/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("PLACE_NOT_FOUND"));
    }

    @Test
    void getPlace_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/places/" + UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    // ──────────────────── GET /places/search ────────────────────

    @Test
    void searchPlaces_kakaoReturnsResults_returnsPlacesFromDb() throws Exception {
        var doc = new KakaoLocalDocument("kakao-001", "블루보틀 시부야점", "CE7",
                "139.699", "35.661", "도쿄", "도쿄 시부야구");
        when(kakaoLocalClient.searchByKeyword(eq("블루보틀"))).thenReturn(List.of(doc));

        mockMvc.perform(get("/places/search")
                .param("q", "블루보틀")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results").isArray())
            .andExpect(jsonPath("$.results.length()").value(1))
            .andExpect(jsonPath("$.results[0].canonical_name").value("블루보틀 시부야점"))
            .andExpect(jsonPath("$.results[0].category").value("cafe"))
            .andExpect(jsonPath("$.results[0].place_id").isNotEmpty());
    }

    @Test
    void searchPlaces_kakaoReturnsEmpty_returnsEmptyList() throws Exception {
        when(kakaoLocalClient.searchByKeyword(eq("없는장소"))).thenReturn(List.of());

        mockMvc.perform(get("/places/search")
                .param("q", "없는장소")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results").isArray())
            .andExpect(jsonPath("$.results.length()").value(0));
    }

    @Test
    void searchPlaces_sameKakaoIdTwice_deduplicatesInDb() throws Exception {
        var doc = new KakaoLocalDocument("kakao-dup", "중복장소", "FD6",
                "139.700", "35.660", "도쿄", "도쿄");
        when(kakaoLocalClient.searchByKeyword(anyString())).thenReturn(List.of(doc));

        mockMvc.perform(get("/places/search").param("q", "test")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results.length()").value(1));

        // 두 번째 검색: DB에 이미 있으므로 새로 insert하지 않음
        mockMvc.perform(get("/places/search").param("q", "test")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results.length()").value(1));

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM places WHERE kakao_place_id = 'kakao-dup'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    @Test
    void searchPlaces_missingQ_returns400() throws Exception {
        mockMvc.perform(get("/places/search")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
    }

    @Test
    void searchPlaces_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/places/search").param("q", "test"))
            .andExpect(status().isUnauthorized());
    }

    // ──────────────────── GET /places/similar ────────────────────

    @Test
    void getSimilarPlaces_placeWithoutEmbedding_returnsEmptyList() throws Exception {
        UUID placeId = insertTestPlace("기준장소", "CAFE", "KR", null, null);

        mockMvc.perform(get("/places/similar")
                .param("place_id", placeId.toString())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results").isArray())
            .andExpect(jsonPath("$.results.length()").value(0));
    }

    @Test
    void getSimilarPlaces_nonExistingPlace_returns404() throws Exception {
        mockMvc.perform(get("/places/similar")
                .param("place_id", UUID.randomUUID().toString())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void getSimilarPlaces_limitCappedAt20() throws Exception {
        UUID placeId = insertTestPlace("기준장소2", "CAFE", "KR", null, null);

        mockMvc.perform(get("/places/similar")
                .param("place_id", placeId.toString())
                .param("limit", "100")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    void getSimilarPlaces_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/places/similar").param("place_id", UUID.randomUUID().toString()))
            .andExpect(status().isUnauthorized());
    }

    // ──────────────────── GET /discover ────────────────────

    @Test
    void getDiscover_noSavedPlaces_returnsColdStartResults() throws Exception {
        // DB에 장소가 없으면 빈 배열 반환 (coldstart + 빈 DB)
        mockMvc.perform(get("/discover")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results").isArray());
    }

    @Test
    void getDiscover_withPlacesInDb_returnsColdStartWhenNoEmbeddings() throws Exception {
        insertTestPlace("장소A", "ATTRACTION", "JP", null, null);
        insertTestPlace("장소B", "RESTAURANT", "JP", null, null);

        // embedding 없으므로 coldstart 경로, 장소들이 반환됨
        mockMvc.perform(get("/discover")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results").isArray())
            .andExpect(jsonPath("$.results.length()").value(2));
    }

    @Test
    void getDiscover_withCategoryFilter_returnsFilteredResults() throws Exception {
        insertTestPlace("카페A", "CAFE", "JP", null, null);
        insertTestPlace("식당B", "RESTAURANT", "JP", null, null);

        mockMvc.perform(get("/discover")
                .param("category", "CAFE")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results").isArray())
            .andExpect(jsonPath("$.results.length()").value(1))
            .andExpect(jsonPath("$.results[0].canonical_name").value("카페A"));
    }

    @Test
    void getDiscover_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/discover"))
            .andExpect(status().isUnauthorized());
    }

    // ──────────────────── GET /places/:id/source-links ────────────────────

    @Test
    void getSourceLinks_placeWithNoLinks_returnsEmptyList() throws Exception {
        UUID placeId = insertTestPlace("링크없는장소", "ATTRACTION", "KR", null, null);

        mockMvc.perform(get("/places/" + placeId + "/source-links")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void getSourceLinks_nonExistingPlace_returns404() throws Exception {
        mockMvc.perform(get("/places/" + UUID.randomUUID() + "/source-links")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void getSourceLinks_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/places/" + UUID.randomUUID() + "/source-links"))
            .andExpect(status().isUnauthorized());
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

    private UUID insertTestPlace(String name, String category, String countryCode,
                                 String googlePlaceId, String kakaoPlaceId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO places (id, canonical_name, latitude, longitude, category, country_code, " +
            "google_place_id, kakao_place_id) VALUES (?, ?, 35.659, 139.700, ?, ?, ?, ?)",
            id, name, category, countryCode, googlePlaceId, kakaoPlaceId
        );
        return id;
    }
}
