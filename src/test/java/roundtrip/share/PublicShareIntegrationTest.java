package roundtrip.share;

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

import java.time.LocalDate;
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
class PublicShareIntegrationTest {

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
            .thenReturn(new SocialIdentity(SocialProvider.KAKAO, "kakao-share-1", "share@example.com"));

        token = signIn();
        userId = getUserId();
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("""
            TRUNCATE TABLE itinerary_items, itineraries, collection_places, collections,
            user_social_accounts, users CASCADE
        """);
        jdbcTemplate.execute("TRUNCATE TABLE places CASCADE");
        redisson.getKeys().flushdb();
    }

    // ──────────────────── GET /public/itineraries/:shareToken ────────────────────

    @Test
    void getSharedItinerary_validToken_returns200WithoutAuth() throws Exception {
        UUID itId = insertItinerary(userId, "도쿄 여행", "share-token-123");
        UUID placeId = insertTestPlace("시부야 교차로");
        insertItineraryItem(itId, placeId, 1, 1);

        // 비인증 요청
        mockMvc.perform(get("/public/itineraries/share-token-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.itinerary_id").value(itId.toString()))
            .andExpect(jsonPath("$.title").value("도쿄 여행"))
            .andExpect(jsonPath("$.destination_region").value("도쿄, 일본"))
            .andExpect(jsonPath("$.party_size").value(2))
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].canonical_name").value("시부야 교차로"));
    }

    @Test
    void getSharedItinerary_invalidToken_returns404() throws Exception {
        mockMvc.perform(get("/public/itineraries/invalid-token"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getSharedItinerary_noItems_returnsEmptyList() throws Exception {
        insertItinerary(userId, "빈 플랜", "empty-token");

        mockMvc.perform(get("/public/itineraries/empty-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    // ──────────────────── GET /public/collections/:shareToken ────────────────────

    @Test
    void getSharedCollection_validToken_returns200WithoutAuth() throws Exception {
        UUID collId = insertCollection(userId, "일본 맛집", "coll-share-abc");
        UUID placeId = insertTestPlace("라멘 맛집");
        insertCollectionPlace(collId, placeId);

        mockMvc.perform(get("/public/collections/coll-share-abc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.collection_id").value(collId.toString()))
            .andExpect(jsonPath("$.name").value("일본 맛집"))
            .andExpect(jsonPath("$.places").isArray())
            .andExpect(jsonPath("$.places.length()").value(1))
            .andExpect(jsonPath("$.places[0].canonical_name").value("라멘 맛집"));
    }

    @Test
    void getSharedCollection_invalidToken_returns404() throws Exception {
        mockMvc.perform(get("/public/collections/invalid-token"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getSharedCollection_noPlaces_returnsEmptyList() throws Exception {
        insertCollection(userId, "빈 컬렉션", "empty-coll-token");

        mockMvc.perform(get("/public/collections/empty-coll-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.places.length()").value(0));
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

    private UUID insertItinerary(UUID userId, String title, String shareToken) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO itineraries (id, user_id, title, destination_region, start_date, end_date,
            party_size, status, visibility, share_token)
            VALUES (?, ?, ?, '도쿄, 일본', ?, ?, 2, 'confirmed', 'public', ?)
        """, id, userId, title, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 4), shareToken);
        return id;
    }

    private UUID insertCollection(UUID userId, String name, String shareToken) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO collections (id, user_id, name, is_default, visibility, share_token)
            VALUES (?, ?, ?, false, 'public', ?)
        """, id, userId, name, shareToken);
        return id;
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

    private void insertItineraryItem(UUID itineraryId, UUID placeId, int dayIndex, int sortOrder) {
        jdbcTemplate.update("""
            INSERT INTO itinerary_items (id, itinerary_id, place_id, day_index, sort_order, planned_duration_minutes)
            VALUES (?, ?, ?, ?, ?, 60)
        """, UUID.randomUUID(), itineraryId, placeId, dayIndex, sortOrder);
    }

    private void insertCollectionPlace(UUID collectionId, UUID placeId) {
        jdbcTemplate.update(
            "INSERT INTO collection_places (collection_id, place_id) VALUES (?, ?)",
            collectionId, placeId
        );
    }
}
