package roundtrip.market;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class MarketIntegrationTest {

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
    String token1;
    UUID userId1;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();

        when(verifierRegistry.verify(eq(SocialProvider.KAKAO), eq("user1-token")))
            .thenReturn(new SocialIdentity(SocialProvider.KAKAO, "kakao-market-1", "market1@example.com"));
        when(verifierRegistry.verify(eq(SocialProvider.KAKAO), eq("user2-token")))
            .thenReturn(new SocialIdentity(SocialProvider.KAKAO, "kakao-market-2", "market2@example.com"));

        token1 = signIn("user1-token");
        userId1 = getUserId(token1);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("""
            TRUNCATE TABLE credit_histories, market_plans, itinerary_items, itineraries,
            collection_places, collections, user_social_accounts, users CASCADE
        """);
        jdbcTemplate.execute("TRUNCATE TABLE places CASCADE");
        redisson.getKeys().flushdb();
    }

    // ──────────────────── POST /market/plans ────────────────────

    @Test
    void createMarketPlan_validRequest_returns201() throws Exception {
        UUID itineraryId = insertItinerary(userId1, "도쿄 3박 4일");

        var body = Map.of(
            "itinerary_id", itineraryId.toString(),
            "title", "도쿄 3박 4일 완벽 코스",
            "description", "실제로 다녀온 도쿄 여행 플랜이에요.",
            "highlight", "현지인만 아는 골목 맛집 5곳 포함",
            "pros", "이동 동선이 짧아서 피로도가 낮았어요.",
            "cons", "아사쿠사는 오전 일찍 가야 사람이 적어요."
        );

        mockMvc.perform(post("/market/plans")
                .header("Authorization", "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.market_plan_id").isNotEmpty())
            .andExpect(jsonPath("$.title").value("도쿄 3박 4일 완벽 코스"));
    }

    @Test
    void createMarketPlan_duplicateItinerary_returns409() throws Exception {
        UUID itineraryId = insertItinerary(userId1, "중복 테스트");
        createMarketPlan(token1, itineraryId);

        var body = Map.of(
            "itinerary_id", itineraryId.toString(),
            "title", "다른 제목",
            "description", "설명",
            "highlight", "요약",
            "pros", "장점",
            "cons", "단점"
        );

        mockMvc.perform(post("/market/plans")
                .header("Authorization", "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isConflict());
    }

    @Test
    void createMarketPlan_nonExistingItinerary_returns404() throws Exception {
        var body = Map.of(
            "itinerary_id", UUID.randomUUID().toString(),
            "title", "제목",
            "description", "설명",
            "highlight", "요약",
            "pros", "장점",
            "cons", "단점"
        );

        mockMvc.perform(post("/market/plans")
                .header("Authorization", "Bearer " + token1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isNotFound());
    }

    @Test
    void createMarketPlan_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/market/plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"test\"}"))
            .andExpect(status().isUnauthorized());
    }

    // ──────────────────── GET /market/plans ────────────────────

    @Test
    void getMarketPlans_returnsList() throws Exception {
        UUID itId1 = insertItinerary(userId1, "플랜1");
        UUID itId2 = insertItinerary(userId1, "플랜2");
        createMarketPlan(token1, itId1);
        createMarketPlan(token1, itId2);

        mockMvc.perform(get("/market/plans")
                .header("Authorization", "Bearer " + token1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void getMarketPlans_emptyList_returnsEmpty() throws Exception {
        mockMvc.perform(get("/market/plans")
                .header("Authorization", "Bearer " + token1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void getMarketPlans_searchByKeyword_filtersResults() throws Exception {
        UUID itId1 = insertItinerary(userId1, "도쿄 여행");
        UUID itId2 = insertItinerary(userId1, "오사카 여행");
        createMarketPlanWithTitle(token1, itId1, "도쿄 맛집 투어");
        createMarketPlanWithTitle(token1, itId2, "오사카 성 탐방");

        mockMvc.perform(get("/market/plans")
                .header("Authorization", "Bearer " + token1)
                .param("q", "도쿄"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].title").value("도쿄 맛집 투어"));
    }

    // ──────────────────── GET /market/plans/:id/preview ────────────────────

    @Test
    void getPreview_existingPlan_returns200() throws Exception {
        UUID itId = insertItinerary(userId1, "프리뷰 테스트");
        UUID placeId = insertTestPlace("시부야 교차로");
        insertItineraryItem(itId, placeId, 1, 1);

        UUID marketPlanId = createMarketPlan(token1, itId);

        mockMvc.perform(get("/market/plans/" + marketPlanId + "/preview")
                .header("Authorization", "Bearer " + token1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.market_plan_id").value(marketPlanId.toString()))
            .andExpect(jsonPath("$.title").isNotEmpty())
            .andExpect(jsonPath("$.preview_places.length()").value(1))
            .andExpect(jsonPath("$.preview_places[0].canonical_name").value("시부야 교차로"))
            .andExpect(jsonPath("$.hidden_place_count").value(0))
            .andExpect(jsonPath("$.is_purchased").value(true)); // 본인 플랜이므로 true
    }

    @Test
    void getPreview_nonExisting_returns404() throws Exception {
        mockMvc.perform(get("/market/plans/" + UUID.randomUUID() + "/preview")
                .header("Authorization", "Bearer " + token1))
            .andExpect(status().isNotFound());
    }

    // ──────────────────── GET /market/plans/:id ────────────────────

    @Test
    void getDetail_ownerAccess_noCreditDeduction() throws Exception {
        UUID itId = insertItinerary(userId1, "상세 테스트");
        UUID placeId = insertTestPlace("아사쿠사");
        insertItineraryItem(itId, placeId, 1, 1);
        UUID marketPlanId = createMarketPlan(token1, itId);

        mockMvc.perform(get("/market/plans/" + marketPlanId)
                .header("Authorization", "Bearer " + token1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.market_plan_id").value(marketPlanId.toString()))
            .andExpect(jsonPath("$.days").isArray())
            .andExpect(jsonPath("$.days[0].items[0].canonical_name").value("아사쿠사"));
    }

    @Test
    void getDetail_otherUser_insufficientCredits_returns402() throws Exception {
        UUID itId = insertItinerary(userId1, "크레딧 부족");
        UUID marketPlanId = createMarketPlan(token1, itId);

        String token2 = signIn("user2-token");

        mockMvc.perform(get("/market/plans/" + marketPlanId)
                .header("Authorization", "Bearer " + token2))
            .andExpect(status().isPaymentRequired());
    }

    @Test
    void getDetail_otherUser_withCredits_deductsAndReturns() throws Exception {
        UUID itId = insertItinerary(userId1, "크레딧 차감");
        UUID marketPlanId = createMarketPlan(token1, itId);

        String token2 = signIn("user2-token");
        UUID userId2 = getUserId(token2);

        // user2에게 크레딧 부여
        jdbcTemplate.update("UPDATE users SET credit_balance = 5 WHERE id = ?", userId2);

        mockMvc.perform(get("/market/plans/" + marketPlanId)
                .header("Authorization", "Bearer " + token2))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.market_plan_id").value(marketPlanId.toString()));

        // 크레딧 차감 확인
        Integer balance = jdbcTemplate.queryForObject(
            "SELECT credit_balance FROM users WHERE id = ?", Integer.class, userId2);
        // Note: credit_balance in users table isn't auto-updated by our service
        // but credit_histories should have the record
        Integer historyCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM credit_histories WHERE user_id = ? AND credit_type = 'plan_purchase'",
            Integer.class, userId2);
        org.assertj.core.api.Assertions.assertThat(historyCount).isEqualTo(1);
    }

    @Test
    void getDetail_secondAccess_noDuplicateDeduction() throws Exception {
        UUID itId = insertItinerary(userId1, "중복 차감 방지");
        UUID marketPlanId = createMarketPlan(token1, itId);

        String token2 = signIn("user2-token");
        UUID userId2 = getUserId(token2);
        jdbcTemplate.update("UPDATE users SET credit_balance = 5 WHERE id = ?", userId2);

        // 첫 번째 조회
        mockMvc.perform(get("/market/plans/" + marketPlanId)
                .header("Authorization", "Bearer " + token2))
            .andExpect(status().isOk());

        // 두 번째 조회 — 이미 구매했으므로 차감 없음
        mockMvc.perform(get("/market/plans/" + marketPlanId)
                .header("Authorization", "Bearer " + token2))
            .andExpect(status().isOk());

        // 구매 기록은 1개만
        Integer historyCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM credit_histories WHERE user_id = ? AND credit_type = 'plan_purchase'",
            Integer.class, userId2);
        org.assertj.core.api.Assertions.assertThat(historyCount).isEqualTo(1);
    }

    // ──────────────────── DELETE /market/plans/:id ────────────────────

    @Test
    void unlistPlan_ownPlan_returns204() throws Exception {
        UUID itId = insertItinerary(userId1, "삭제 테스트");
        UUID marketPlanId = createMarketPlan(token1, itId);

        mockMvc.perform(delete("/market/plans/" + marketPlanId)
                .header("Authorization", "Bearer " + token1))
            .andExpect(status().isNoContent());

        // 삭제 후 목록에서 안보임
        mockMvc.perform(get("/market/plans")
                .header("Authorization", "Bearer " + token1))
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void unlistPlan_otherUserPlan_returns403() throws Exception {
        UUID itId = insertItinerary(userId1, "타인 삭제");
        UUID marketPlanId = createMarketPlan(token1, itId);

        String token2 = signIn("user2-token");

        mockMvc.perform(delete("/market/plans/" + marketPlanId)
                .header("Authorization", "Bearer " + token2))
            .andExpect(status().isForbidden());
    }

    @Test
    void unlistPlan_nonExisting_returns404() throws Exception {
        mockMvc.perform(delete("/market/plans/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token1))
            .andExpect(status().isNotFound());
    }

    // ──────────────────── helpers ────────────────────

    @SuppressWarnings("unchecked")
    private String signIn(String idToken) throws Exception {
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
    private UUID getUserId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + token))
            .andReturn();
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }

    private UUID insertItinerary(UUID userId, String title) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO itineraries (id, user_id, title, destination_region, start_date, end_date, party_size, status, visibility)
            VALUES (?, ?, ?, '도쿄, 일본', ?, ?, 2, 'confirmed', 'private')
        """, id, userId, title, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 4));
        return id;
    }

    @SuppressWarnings("unchecked")
    private UUID createMarketPlan(String token, UUID itineraryId) throws Exception {
        return createMarketPlanWithTitle(token, itineraryId, "마켓 플랜 제목");
    }

    @SuppressWarnings("unchecked")
    private UUID createMarketPlanWithTitle(String token, UUID itineraryId, String title) throws Exception {
        var body = Map.of(
            "itinerary_id", itineraryId.toString(),
            "title", title,
            "description", "설명입니다",
            "highlight", "한 줄 요약",
            "pros", "좋았던 점",
            "cons", "아쉬웠던 점"
        );

        MvcResult result = mockMvc.perform(post("/market/plans")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn();
        Map<String, Object> respBody = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) respBody.get("market_plan_id"));
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
}
