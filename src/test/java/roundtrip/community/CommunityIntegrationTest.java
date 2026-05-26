package roundtrip.community;

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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class CommunityIntegrationTest {

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

        when(verifierRegistry.verify(eq(SocialProvider.KAKAO), eq("user1-token")))
            .thenReturn(new SocialIdentity(SocialProvider.KAKAO, "kakao-user-1", "user1@example.com"));
        when(verifierRegistry.verify(eq(SocialProvider.KAKAO), eq("user2-token")))
            .thenReturn(new SocialIdentity(SocialProvider.KAKAO, "kakao-user-2", "user2@example.com"));
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("""
            TRUNCATE TABLE post_comments, post_likes, post_tagged_itineraries, post_tagged_places,
            community_posts, itinerary_items, itineraries, collection_places, collections,
            user_follows, user_social_accounts, users CASCADE
        """);
        redisson.getKeys().flushdb();
    }

    // ──────────────────── POST /community/posts ────────────────────

    @Test
    void createPost_validRequest_returns201() throws Exception {
        String token = signIn("user1-token");

        mockMvc.perform(post("/community/posts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("content", "도쿄 여행 다녀왔어요"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.post_id").isNotEmpty())
            .andExpect(jsonPath("$.content").value("도쿄 여행 다녀왔어요"))
            .andExpect(jsonPath("$.like_count").value(0))
            .andExpect(jsonPath("$.comment_count").value(0))
            .andExpect(jsonPath("$.is_liked").value(false));
    }

    @Test
    void createPost_withTaggedPlaces_returns201() throws Exception {
        String token = signIn("user1-token");
        UUID placeId = insertTestPlace("시부야 교차로");

        var body = Map.of(
            "content", "시부야 다녀왔어요",
            "tagged_place_ids", List.of(placeId.toString())
        );

        mockMvc.perform(post("/community/posts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tagged_places.length()").value(1))
            .andExpect(jsonPath("$.tagged_places[0].canonical_name").value("시부야 교차로"));
    }

    @Test
    void createPost_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/community/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"test\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createPost_emptyContent_returns400() throws Exception {
        String token = signIn("user1-token");

        mockMvc.perform(post("/community/posts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    // ──────────────────── GET /community/posts ────────────────────

    @Test
    void getFeed_returnsPostsList() throws Exception {
        String token = signIn("user1-token");
        createPost(token, "첫 번째 포스트");
        createPost(token, "두 번째 포스트");

        mockMvc.perform(get("/community/posts")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void getFeed_followingFilter_returnsOnlyFollowedPosts() throws Exception {
        String token1 = signIn("user1-token");
        String token2 = signIn("user2-token");

        createPost(token2, "user2의 포스트");

        // user1이 user2를 팔로우하지 않으면 following 피드 비어있음
        mockMvc.perform(get("/community/posts")
                .header("Authorization", "Bearer " + token1)
                .param("feed", "following"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));

        // user2의 userId 가져오기
        UUID user2Id = getUserId(token2);

        // user1이 user2를 팔로우
        mockMvc.perform(post("/users/" + user2Id + "/follow")
                .header("Authorization", "Bearer " + token1))
            .andExpect(status().isCreated());

        // 이제 following 피드에 user2의 포스트가 보임
        mockMvc.perform(get("/community/posts")
                .header("Authorization", "Bearer " + token1)
                .param("feed", "following"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].content").value("user2의 포스트"));
    }

    // ──────────────────── GET /community/posts/:postId ────────────────────

    @Test
    void getPost_existingPost_returns200() throws Exception {
        String token = signIn("user1-token");
        UUID postId = createPost(token, "상세 조회 테스트");

        mockMvc.perform(get("/community/posts/" + postId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.post_id").value(postId.toString()))
            .andExpect(jsonPath("$.content").value("상세 조회 테스트"));
    }

    @Test
    void getPost_nonExisting_returns404() throws Exception {
        String token = signIn("user1-token");

        mockMvc.perform(get("/community/posts/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    // ──────────────────── DELETE /community/posts/:postId ────────────────────

    @Test
    void deletePost_ownPost_returns204() throws Exception {
        String token = signIn("user1-token");
        UUID postId = createPost(token, "삭제할 포스트");

        mockMvc.perform(delete("/community/posts/" + postId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        // 삭제 확인
        mockMvc.perform(get("/community/posts/" + postId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void deletePost_otherUserPost_returns403() throws Exception {
        String token1 = signIn("user1-token");
        UUID postId = createPost(token1, "user1의 포스트");

        String token2 = signIn("user2-token");

        mockMvc.perform(delete("/community/posts/" + postId)
                .header("Authorization", "Bearer " + token2))
            .andExpect(status().isForbidden());
    }

    // ──────────────────── POST /community/posts/:postId/like ────────────────────

    @Test
    void likePost_validPost_returns201() throws Exception {
        String token = signIn("user1-token");
        UUID postId = createPost(token, "좋아요 테스트");

        mockMvc.perform(post("/community/posts/" + postId + "/like")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.like_count").value(1));
    }

    @Test
    void likePost_alreadyLiked_returns409() throws Exception {
        String token = signIn("user1-token");
        UUID postId = createPost(token, "중복 좋아요");

        mockMvc.perform(post("/community/posts/" + postId + "/like")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/community/posts/" + postId + "/like")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isConflict());
    }

    // ──────────────────── DELETE /community/posts/:postId/like ────────────────────

    @Test
    void unlikePost_afterLike_returns200() throws Exception {
        String token = signIn("user1-token");
        UUID postId = createPost(token, "좋아요 취소 테스트");

        mockMvc.perform(post("/community/posts/" + postId + "/like")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated());

        mockMvc.perform(delete("/community/posts/" + postId + "/like")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.like_count").value(0));
    }

    // ──────────────────── POST /community/posts/:postId/comments ────────────────────

    @Test
    void createComment_validRequest_returns201() throws Exception {
        String token = signIn("user1-token");
        UUID postId = createPost(token, "댓글 테스트");

        mockMvc.perform(post("/community/posts/" + postId + "/comments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"좋은 여행이네요!\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.comment_id").isNotEmpty())
            .andExpect(jsonPath("$.content").value("좋은 여행이네요!"));
    }

    // ──────────────────── GET /community/posts/:postId/comments ────────────────────

    @Test
    void getComments_afterCreating_returnsList() throws Exception {
        String token = signIn("user1-token");
        UUID postId = createPost(token, "댓글 목록 테스트");

        mockMvc.perform(post("/community/posts/" + postId + "/comments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"첫 번째 댓글\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/community/posts/" + postId + "/comments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"두 번째 댓글\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/community/posts/" + postId + "/comments")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2));
    }

    // ──────────────────── DELETE /community/posts/:postId/comments/:commentId ────────────────────

    @Test
    void deleteComment_ownComment_returns204() throws Exception {
        String token = signIn("user1-token");
        UUID postId = createPost(token, "댓글 삭제 테스트");
        UUID commentId = createComment(token, postId, "삭제할 댓글");

        mockMvc.perform(delete("/community/posts/" + postId + "/comments/" + commentId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleteComment_otherUserComment_returns403() throws Exception {
        String token1 = signIn("user1-token");
        UUID postId = createPost(token1, "포스트");
        UUID commentId = createComment(token1, postId, "user1의 댓글");

        String token2 = signIn("user2-token");

        mockMvc.perform(delete("/community/posts/" + postId + "/comments/" + commentId)
                .header("Authorization", "Bearer " + token2))
            .andExpect(status().isForbidden());
    }

    // ──────────────────── GET /users/:userId/profile ────────────────────

    @Test
    void getUserProfile_existingUser_returns200() throws Exception {
        String token1 = signIn("user1-token");
        String token2 = signIn("user2-token");
        UUID user2Id = getUserId(token2);

        mockMvc.perform(get("/users/" + user2Id + "/profile")
                .header("Authorization", "Bearer " + token1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user_id").value(user2Id.toString()))
            .andExpect(jsonPath("$.follower_count").value(0))
            .andExpect(jsonPath("$.following_count").value(0))
            .andExpect(jsonPath("$.post_count").value(0))
            .andExpect(jsonPath("$.is_following").value(false));
    }

    @Test
    void getUserProfile_nonExistingUser_returns404() throws Exception {
        String token = signIn("user1-token");

        mockMvc.perform(get("/users/" + UUID.randomUUID() + "/profile")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    // ──────────────────── POST /users/:userId/follow ────────────────────

    @Test
    void follow_validUser_returns201() throws Exception {
        String token1 = signIn("user1-token");
        String token2 = signIn("user2-token");
        UUID user2Id = getUserId(token2);

        mockMvc.perform(post("/users/" + user2Id + "/follow")
                .header("Authorization", "Bearer " + token1))
            .andExpect(status().isCreated());

        // 프로필에서 팔로우 확인
        mockMvc.perform(get("/users/" + user2Id + "/profile")
                .header("Authorization", "Bearer " + token1))
            .andExpect(jsonPath("$.follower_count").value(1))
            .andExpect(jsonPath("$.is_following").value(true));
    }

    @Test
    void follow_selfFollow_returns403() throws Exception {
        String token = signIn("user1-token");
        UUID userId = getUserId(token);

        mockMvc.perform(post("/users/" + userId + "/follow")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void follow_alreadyFollowing_returns409() throws Exception {
        String token1 = signIn("user1-token");
        String token2 = signIn("user2-token");
        UUID user2Id = getUserId(token2);

        mockMvc.perform(post("/users/" + user2Id + "/follow")
                .header("Authorization", "Bearer " + token1))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/users/" + user2Id + "/follow")
                .header("Authorization", "Bearer " + token1))
            .andExpect(status().isConflict());
    }

    // ──────────────────── DELETE /users/:userId/follow ────────────────────

    @Test
    void unfollow_afterFollow_returns204() throws Exception {
        String token1 = signIn("user1-token");
        String token2 = signIn("user2-token");
        UUID user2Id = getUserId(token2);

        mockMvc.perform(post("/users/" + user2Id + "/follow")
                .header("Authorization", "Bearer " + token1))
            .andExpect(status().isCreated());

        mockMvc.perform(delete("/users/" + user2Id + "/follow")
                .header("Authorization", "Bearer " + token1))
            .andExpect(status().isNoContent());

        // 프로필에서 언팔로우 확인
        mockMvc.perform(get("/users/" + user2Id + "/profile")
                .header("Authorization", "Bearer " + token1))
            .andExpect(jsonPath("$.follower_count").value(0))
            .andExpect(jsonPath("$.is_following").value(false));
    }

    // ──────────────────── Pagination ────────────────────

    @Test
    void getFeed_pagination_worksCorrectly() throws Exception {
        String token = signIn("user1-token");

        // 5개 포스트 생성
        for (int i = 0; i < 5; i++) {
            createPost(token, "포스트 " + i);
        }

        // limit=2로 조회
        MvcResult r1 = mockMvc.perform(get("/community/posts")
                .header("Authorization", "Bearer " + token)
                .param("limit", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.next_cursor").isNotEmpty())
            .andReturn();

        @SuppressWarnings("unchecked")
        String cursor = (String) objectMapper.readValue(
            r1.getResponse().getContentAsString(), Map.class).get("next_cursor");

        // 커서로 다음 페이지 조회
        mockMvc.perform(get("/community/posts")
                .header("Authorization", "Bearer " + token)
                .param("limit", "2")
                .param("cursor", cursor))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.next_cursor").isNotEmpty());
    }

    // ──────────────────── Comment count update ────────────────────

    @Test
    void commentCount_updatesOnCreateAndDelete() throws Exception {
        String token = signIn("user1-token");
        UUID postId = createPost(token, "댓글 수 테스트");

        UUID commentId = createComment(token, postId, "댓글1");

        // 포스트 상세에서 comment_count 확인
        mockMvc.perform(get("/community/posts/" + postId)
                .header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.comment_count").value(1));

        // 댓글 삭제
        mockMvc.perform(delete("/community/posts/" + postId + "/comments/" + commentId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        // comment_count 0으로 감소 확인
        mockMvc.perform(get("/community/posts/" + postId)
                .header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.comment_count").value(0));
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

    @SuppressWarnings("unchecked")
    private UUID createPost(String token, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/community/posts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("content", content))))
            .andExpect(status().isCreated())
            .andReturn();
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("post_id"));
    }

    @SuppressWarnings("unchecked")
    private UUID createComment(String token, UUID postId, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/community/posts/" + postId + "/comments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("content", content))))
            .andExpect(status().isCreated())
            .andReturn();
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("comment_id"));
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
