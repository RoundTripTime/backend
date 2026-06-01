package roundtrip.itinerary;

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
import roundtrip.itinerary.application.PlanningAgentService;
import roundtrip.itinerary.infrastructure.myrealtrip.MyRealTripClient;
import roundtrip.user.domain.entity.SocialProvider;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class ItineraryIntegrationTest {

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
    MyRealTripClient myRealTripClient;

    @MockitoBean
    PlanningAgentService planningAgentService;

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
        jdbcTemplate.execute("TRUNCATE TABLE itinerary_items, itineraries, collection_places, collections, user_social_accounts, users CASCADE");
        redisson.getKeys().flushdb();
    }

    // ──────────────────── GET /itineraries ────────────────────

    @Test
    void getItineraries_empty_returnsEmptyList() throws Exception {
        String token = signIn();

        mockMvc.perform(get("/itineraries")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void getItineraries_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/itineraries"))
            .andExpect(status().isUnauthorized());
    }

    // ──────────────────── POST /itineraries ────────────────────

    @Test
    void createItinerary_validRequest_returns201() throws Exception {
        String token = signIn();
        var body = Map.of(
            "title", "도쿄 3박 4일",
            "destination_region", "도쿄, 일본",
            "start_date", "2024-12-20",
            "end_date", "2024-12-23",
            "party_size", 2
        );

        mockMvc.perform(post("/itineraries")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.itinerary_id").isNotEmpty())
            .andExpect(jsonPath("$.title").value("도쿄 3박 4일"))
            .andExpect(jsonPath("$.destination_region").value("도쿄, 일본"))
            .andExpect(jsonPath("$.start_date").value("2024-12-20"))
            .andExpect(jsonPath("$.end_date").value("2024-12-23"))
            .andExpect(jsonPath("$.party_size").value(2))
            .andExpect(jsonPath("$.visibility").value("private"))
            .andExpect(jsonPath("$.status").value("draft"))
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void createItinerary_missingTitle_returns400() throws Exception {
        String token = signIn();
        var body = Map.of(
            "destination_region", "도쿄",
            "start_date", "2024-12-20",
            "end_date", "2024-12-23",
            "party_size", 2
        );

        mockMvc.perform(post("/itineraries")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ──────────────────── GET /itineraries/:id ────────────────────

    @Test
    void getItinerary_existing_returns200WithItems() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);

        mockMvc.perform(get("/itineraries/" + itineraryId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.itinerary_id").value(itineraryId.toString()))
            .andExpect(jsonPath("$.title").value("도쿄 3박 4일"))
            .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void getItinerary_notOwned_returns404() throws Exception {
        String token1 = signIn();
        UUID itineraryId = createItinerary(token1);

        when(verifierRegistry.verify(eq(SocialProvider.KAKAO), eq("other-token")))
            .thenReturn(new SocialIdentity(SocialProvider.KAKAO, "other-social", "other@example.com"));
        String token2 = signInWith("other-token");

        mockMvc.perform(get("/itineraries/" + itineraryId)
                .header("Authorization", "Bearer " + token2))
            .andExpect(status().isNotFound());
    }

    @Test
    void getItinerary_nonExisting_returns404() throws Exception {
        String token = signIn();

        mockMvc.perform(get("/itineraries/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    // ──────────────────── PATCH /itineraries/:id ────────────────────

    @Test
    void updateItinerary_ownedItinerary_returnsUpdated() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);

        var body = Map.of("title", "오사카 4박 5일", "visibility", "public", "status", "confirmed");

        mockMvc.perform(patch("/itineraries/" + itineraryId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("오사카 4박 5일"))
            .andExpect(jsonPath("$.visibility").value("public"))
            .andExpect(jsonPath("$.status").value("confirmed"));
    }

    // ──────────────────── DELETE /itineraries/:id ────────────────────

    @Test
    void deleteItinerary_owned_returns204() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);

        mockMvc.perform(delete("/itineraries/" + itineraryId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/itineraries")
                .header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void deleteItinerary_nonExisting_returns404() throws Exception {
        String token = signIn();

        mockMvc.perform(delete("/itineraries/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    // ──────────────────── POST /itineraries/:id/items ────────────────────

    @Test
    void addItem_validPlace_returns201() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);
        UUID placeId = insertTestPlace("시부야 교차로");

        var body = Map.of(
            "place_id", placeId.toString(),
            "day_index", 1, "sort_order", 1,
            "start_time", "10:00", "end_time", "11:00"
        );

        mockMvc.perform(post("/itineraries/" + itineraryId + "/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.item_id").isNotEmpty())
            .andExpect(jsonPath("$.place_id").value(placeId.toString()))
            .andExpect(jsonPath("$.place_name").value("시부야 교차로"))
            .andExpect(jsonPath("$.day_index").value(1))
            .andExpect(jsonPath("$.sort_order").value(1))
            .andExpect(jsonPath("$.planned_duration_minutes").value(60));
    }

    @Test
    void addItem_placeNotFound_returns404() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);

        var body = Map.of("place_id", UUID.randomUUID().toString());

        mockMvc.perform(post("/itineraries/" + itineraryId + "/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isNotFound());
    }

    @Test
    void addItem_appearsInItineraryDetail() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);
        UUID placeId = insertTestPlace("이치란 라멘");

        addItemToItinerary(token, itineraryId, placeId, null, null, null);

        mockMvc.perform(get("/itineraries/" + itineraryId)
                .header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].place_name").value("이치란 라멘"))
            .andExpect(jsonPath("$.items[0].day_index").isEmpty());
    }

    // ──────────────────── PATCH /itineraries/:id/items/:itemId ────────────────────

    @Test
    void updateItem_changeDayAndTime_returnsUpdatedWithAutoCalcDuration() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);
        UUID placeId = insertTestPlace("센소지");
        UUID itemId = addItemToItinerary(token, itineraryId, placeId, null, null, null);

        var body = Map.of("day_index", 2, "sort_order", 1, "start_time", "13:00", "end_time", "14:30");

        mockMvc.perform(patch("/itineraries/" + itineraryId + "/items/" + itemId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.day_index").value(2))
            .andExpect(jsonPath("$.sort_order").value(1))
            .andExpect(jsonPath("$.planned_duration_minutes").value(90));
    }

    @Test
    void updateItem_nonExistingItem_returns404() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);

        mockMvc.perform(patch("/itineraries/" + itineraryId + "/items/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"day_index\": 1}"))
            .andExpect(status().isNotFound());
    }

    // ──────────────────── DELETE /itineraries/:id/items/:itemId ────────────────────

    @Test
    void removeItem_existing_returns204() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);
        UUID placeId = insertTestPlace("도쿄타워");
        UUID itemId = addItemToItinerary(token, itineraryId, placeId, 1, 1, null);

        mockMvc.perform(delete("/itineraries/" + itineraryId + "/items/" + itemId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/itineraries/" + itineraryId)
                .header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void removeItem_nonExisting_returns404() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);

        mockMvc.perform(delete("/itineraries/" + itineraryId + "/items/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    // ──────────────────── POST /itineraries/:id/items/reorder ────────────────────

    @Test
    void reorderItems_validRequest_returns200() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);
        UUID place1 = insertTestPlace("장소A");
        UUID place2 = insertTestPlace("장소B");
        UUID item1 = addItemToItinerary(token, itineraryId, place1, 1, 1, null);
        UUID item2 = addItemToItinerary(token, itineraryId, place2, 1, 2, null);

        var body = Map.of("items", List.of(
            Map.of("item_id", item1.toString(), "day_index", 2, "sort_order", 2),
            Map.of("item_id", item2.toString(), "day_index", 2, "sort_order", 1)
        ));

        mockMvc.perform(post("/itineraries/" + itineraryId + "/items/reorder")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/itineraries/" + itineraryId)
                .header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.items[0].day_index").value(2))
            .andExpect(jsonPath("$.items[0].sort_order").value(1))
            .andExpect(jsonPath("$.items[1].day_index").value(2))
            .andExpect(jsonPath("$.items[1].sort_order").value(2));
    }

    // ──────────────────── GET /itineraries/:id/share ────────────────────

    @Test
    void getShareLink_returns200WithShareUrl() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);

        mockMvc.perform(get("/itineraries/" + itineraryId + "/share")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.share_url").isNotEmpty())
            .andExpect(jsonPath("$.visibility").value("private"));
    }

    @Test
    void getShareLink_calledTwice_returnsSameToken() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);

        MvcResult r1 = mockMvc.perform(get("/itineraries/" + itineraryId + "/share")
                .header("Authorization", "Bearer " + token))
            .andReturn();
        MvcResult r2 = mockMvc.perform(get("/itineraries/" + itineraryId + "/share")
                .header("Authorization", "Bearer " + token))
            .andReturn();

        @SuppressWarnings("unchecked")
        String url1 = (String) objectMapper.readValue(r1.getResponse().getContentAsString(), Map.class).get("share_url");
        @SuppressWarnings("unchecked")
        String url2 = (String) objectMapper.readValue(r2.getResponse().getContentAsString(), Map.class).get("share_url");

        assertThat(url1).isEqualTo(url2);
    }

    // ──────────────────── GET /itineraries/:id/ota-links ────────────────────

    @Test
    void getOtaLinks_accommodation_withMyRealTrip_returns200() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);

        // 숙소 검색 mock
        when(myRealTripClient.searchAccommodation(eq("도쿄"), anyString(), anyString(), anyInt()))
            .thenReturn(new MyRealTripClient.AccommodationSearchResponse(
                List.of(new MyRealTripClient.AccommodationItem(
                    1484076L, "롯데호텔", 346690L, 398000L, 5, "4.7", 1783, null)),
                1, 0, 5));
        when(myRealTripClient.createMyLink(eq("https://www.myrealtrip.com/offers/1484076")))
            .thenReturn(new MyRealTripClient.MyLinkResponse("https://myrealt.rip/acc123", 1234567L));

        mockMvc.perform(get("/itineraries/" + itineraryId + "/ota-links")
                .header("Authorization", "Bearer " + token)
                .param("type", "accommodation"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ota_url").value("https://myrealt.rip/acc123"));
    }

    @Test
    void getOtaLinks_flight_withMyRealTrip_returns200() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);

        // 항공 랜딩 URL mock
        when(myRealTripClient.createFlightLandingUrl(eq("ICN"), eq("NRT"), eq("RT"), anyString(), anyString(), anyInt()))
            .thenReturn("https://flights.myrealtrip.com/air/agent/b2c/AIR/ICN/NRT/offers.k1?depdt=2024-12-20");
        when(myRealTripClient.createMyLink(startsWith("https://flights.myrealtrip.com/")))
            .thenReturn(new MyRealTripClient.MyLinkResponse("https://myrealt.rip/flt456", 2345678L));

        mockMvc.perform(get("/itineraries/" + itineraryId + "/ota-links")
                .header("Authorization", "Bearer " + token)
                .param("type", "flight"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ota_url").value("https://myrealt.rip/flt456"));
    }

    @Test
    void getOtaLinks_activity_withMyRealTrip_returns200() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);

        // 투어/티켓 검색 mock
        when(myRealTripClient.searchTna(eq("도쿄"), eq("도쿄")))
            .thenReturn(new MyRealTripClient.TnaSearchResponse(
                List.of(new MyRealTripClient.TnaItem(
                    "5869248", "도쿄 타워 입장권", "도쿄 · 티켓", 12657L, "12,657원",
                    "티켓", 4.83, 1250, null,
                    "https://experiences.myrealtrip.com/products/5869248", null, List.of())),
                1, 1, 5, false));
        when(myRealTripClient.createMyLink(eq("https://experiences.myrealtrip.com/products/5869248")))
            .thenReturn(new MyRealTripClient.MyLinkResponse("https://myrealt.rip/act789", 3456789L));

        mockMvc.perform(get("/itineraries/" + itineraryId + "/ota-links")
                .header("Authorization", "Bearer " + token)
                .param("type", "activity"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ota_url").value("https://myrealt.rip/act789"));
    }

    @Test
    void getOtaLinks_accommodation_apiFails_returnsFallbackUrl() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);

        when(myRealTripClient.searchAccommodation(anyString(), anyString(), anyString(), anyInt()))
            .thenThrow(new RuntimeException("API 오류"));

        mockMvc.perform(get("/itineraries/" + itineraryId + "/ota-links")
                .header("Authorization", "Bearer " + token)
                .param("type", "accommodation"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ota_url").value("https://www.myrealtrip.com/accommodations?keyword=도쿄"));
    }

    @Test
    void getOtaLinks_invalidType_returns400() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);

        mockMvc.perform(get("/itineraries/" + itineraryId + "/ota-links")
                .header("Authorization", "Bearer " + token)
                .param("type", "invalid"))
            .andExpect(status().isBadRequest());
    }

    // ──────────────────── GET /itineraries — place_count 확인 ────────────────────

    @Test
    void getItineraries_showsCorrectPlaceCount() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);
        UUID place1 = insertTestPlace("장소1");
        UUID place2 = insertTestPlace("장소2");
        addItemToItinerary(token, itineraryId, place1, 1, 1, null);
        addItemToItinerary(token, itineraryId, place2, 1, 2, null);

        mockMvc.perform(get("/itineraries")
                .header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.items[0].place_count").value(2));
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
    private UUID createItinerary(String token) throws Exception {
        var reqBody = Map.of(
            "title", "도쿄 3박 4일",
            "destination_region", "도쿄, 일본",
            "start_date", "2024-12-20",
            "end_date", "2024-12-23",
            "party_size", 2
        );
        MvcResult result = mockMvc.perform(post("/itineraries")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqBody)))
            .andExpect(status().isCreated())
            .andReturn();
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("itinerary_id"));
    }

    @SuppressWarnings("unchecked")
    private UUID addItemToItinerary(String token, UUID itineraryId, UUID placeId,
                                    Integer dayIndex, Integer sortOrder, Integer ignored) throws Exception {
        var reqBody = new java.util.HashMap<String, Object>();
        reqBody.put("place_id", placeId.toString());
        if (dayIndex != null) reqBody.put("day_index", dayIndex);
        if (sortOrder != null) reqBody.put("sort_order", sortOrder);

        MvcResult result = mockMvc.perform(post("/itineraries/" + itineraryId + "/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqBody)))
            .andExpect(status().isCreated())
            .andReturn();
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("item_id"));
    }

    // ──────────────── POST /itineraries/:id/agent ────────────────

    @Test
    void agentChat_validRequest_returns200() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);

        when(planningAgentService.chat(any(), eq(itineraryId), eq("일정 요약해줘"), any()))
            .thenReturn(new PlanningAgentService.AgentResponse(
                "현재 일정을 요약해드릴게요.", List.of(), false));

        var body = Map.of("message", "일정 요약해줘");
        mockMvc.perform(post("/itineraries/" + itineraryId + "/agent")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply").value("현재 일정을 요약해드릴게요."))
            .andExpect(jsonPath("$.tool_results").isArray())
            .andExpect(jsonPath("$.itinerary_updated").value(false));
    }

    @Test
    void agentChat_withHistory_returns200() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);

        when(planningAgentService.chat(any(), eq(itineraryId), eq("비슷한 카페 찾아줘"), any()))
            .thenReturn(new PlanningAgentService.AgentResponse(
                "비슷한 카페를 찾았어요!",
                List.of(new PlanningAgentService.ToolResult("search_similar_places",
                    List.of(Map.of("place_id", "uuid", "canonical_name", "블루보틀")))),
                false));

        var body = Map.of(
            "message", "비슷한 카페 찾아줘",
            "history", List.of(
                Map.of("role", "user", "content", "동선 최적화해줘"),
                Map.of("role", "assistant", "content", "거리 기준으로 재배열했어요.")
            )
        );
        mockMvc.perform(post("/itineraries/" + itineraryId + "/agent")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply").value("비슷한 카페를 찾았어요!"))
            .andExpect(jsonPath("$.tool_results[0].tool").value("search_similar_places"))
            .andExpect(jsonPath("$.itinerary_updated").value(false));
    }

    @Test
    void agentChat_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/itineraries/" + UUID.randomUUID() + "/agent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("message", "test"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void agentChat_emptyMessage_returns400() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);

        mockMvc.perform(post("/itineraries/" + itineraryId + "/agent")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("message", ""))))
            .andExpect(status().isBadRequest());
    }

    // ──────────────── start_time / end_time ────────────────

    @Test
    void addItem_withStartTimeAndEndTime_autoCalcsDuration() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);
        UUID placeId = insertTestPlace("하라주쿠");

        var body = Map.of(
            "place_id", placeId.toString(),
            "day_index", 1,
            "sort_order", 1,
            "start_time", "09:00",
            "end_time", "11:00"
        );

        mockMvc.perform(post("/itineraries/" + itineraryId + "/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.place_name").value("하라주쿠"))
            .andExpect(jsonPath("$.start_time").value("09:00"))
            .andExpect(jsonPath("$.end_time").value("11:00"))
            .andExpect(jsonPath("$.planned_duration_minutes").value(120));
    }

    @Test
    void addItem_withoutTime_returnsNullDurationAndTimes() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);
        UUID placeId = insertTestPlace("아키하바라");

        var body = Map.of("place_id", placeId.toString());

        mockMvc.perform(post("/itineraries/" + itineraryId + "/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.start_time").doesNotExist())
            .andExpect(jsonPath("$.end_time").doesNotExist())
            .andExpect(jsonPath("$.planned_duration_minutes").doesNotExist());
    }

    @Test
    void updateItem_setStartTimeAndEndTime_autoCalcsDuration() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);
        UUID placeId = insertTestPlace("긴자");
        UUID itemId = addItemToItinerary(token, itineraryId, placeId, 1, 1, null);

        var body = Map.of(
            "day_index", 1,
            "sort_order", 1,
            "start_time", "14:00",
            "end_time", "15:30"
        );

        mockMvc.perform(patch("/itineraries/" + itineraryId + "/items/" + itemId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.start_time").value("14:00"))
            .andExpect(jsonPath("$.end_time").value("15:30"))
            .andExpect(jsonPath("$.planned_duration_minutes").value(90));
    }

    @Test
    void getItinerary_itemsIncludeTimeAndDuration() throws Exception {
        String token = signIn();
        UUID itineraryId = createItinerary(token);
        UUID placeId = insertTestPlace("롯폰기");

        var body = Map.of(
            "place_id", placeId.toString(),
            "day_index", 1,
            "sort_order", 1,
            "start_time", "18:00",
            "end_time", "21:00"
        );
        mockMvc.perform(post("/itineraries/" + itineraryId + "/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/itineraries/" + itineraryId)
                .header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.items[0].start_time").value("18:00"))
            .andExpect(jsonPath("$.items[0].end_time").value("21:00"))
            .andExpect(jsonPath("$.items[0].planned_duration_minutes").value(180));
    }

    // ──────────────── helpers ────────────────

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
