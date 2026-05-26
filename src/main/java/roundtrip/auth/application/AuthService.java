package roundtrip.auth.application;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roundtrip.auth.domain.IssuedTokens;
import roundtrip.auth.domain.SocialIdentity;
import roundtrip.auth.domain.TokenType;
import roundtrip.auth.infrastructure.jwt.JwtTokenProvider;
import roundtrip.auth.infrastructure.refresh.RefreshTokenStore;
import roundtrip.auth.infrastructure.social.SocialIdTokenVerifierRegistry;
import roundtrip.collection.application.CollectionService;
import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;
import roundtrip.user.domain.entity.SocialProvider;
import roundtrip.user.domain.entity.User;
import roundtrip.user.domain.entity.UserSocialAccount;
import roundtrip.user.domain.repository.UserRepository;
import roundtrip.user.domain.repository.UserSocialAccountRepository;
import roundtrip.user.domain.service.NicknameGenerator;
import roundtrip.user.domain.service.RoboHashAvatar;
import roundtrip.user.domain.vo.Email;
import roundtrip.user.domain.vo.Nickname;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DEFAULT_LOCALE = "ko-KR";
    private static final String UNKNOWN_REGION = "Unknown";

    private final SocialIdTokenVerifierRegistry verifierRegistry;
    private final UserRepository userRepository;
    private final UserSocialAccountRepository socialAccountRepository;
    private final NicknameGenerator nicknameGenerator;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final CollectionService collectionService;

    @Transactional
    public SignInResult signInWithSocial(SocialProvider provider, String idToken, Locale clientLocale) {
        SocialIdentity identity = verifierRegistry.verify(provider, idToken);

        var existing = socialAccountRepository.findByProviderAndSocialId(provider, identity.socialId());

        User user;
        boolean isNewUser;
        if (existing.isPresent()) {
            UUID userId = existing.get().getUserId();
            user = userRepository.findById(userId).orElseThrow(() ->
                new BusinessException(ErrorCode.LINKED_USER_NOT_FOUND, "userId=" + userId));
            isNewUser = false;
        } else {
            user = registerNewUser(identity, clientLocale);
            socialAccountRepository.save(UserSocialAccount.link(user.getId(), provider, identity.socialId()));
            collectionService.createDefaultCollection(user.getId());
            isNewUser = true;
        }

        IssuedTokens tokens = jwtTokenProvider.issuePair(user.getId());
        refreshTokenStore.save(user.getId(), tokens.refreshJti(), jwtTokenProvider.refreshExpiry());

        return new SignInResult(user, tokens, isNewUser);
    }

    @Transactional
    public IssuedTokens refresh(String refreshToken) {
        Claims claims = jwtTokenProvider.parseAndValidate(refreshToken, TokenType.REFRESH);
        UUID userId = jwtTokenProvider.extractUserId(claims);
        String oldJti = jwtTokenProvider.extractJti(claims);

        if (!refreshTokenStore.exists(userId, oldJti)) {
            refreshTokenStore.deleteAll(userId);
            throw new BusinessException(ErrorCode.INVALID_TOKEN,
                "재사용되었거나 무효화된 refresh token (userId=" + userId + ")");
        }
        refreshTokenStore.delete(userId, oldJti);

        IssuedTokens tokens = jwtTokenProvider.issuePair(userId);
        refreshTokenStore.save(userId, tokens.refreshJti(), jwtTokenProvider.refreshExpiry());
        return tokens;
    }

    @Transactional
    public void logout(UUID userId) {
        refreshTokenStore.deleteAll(userId);
    }

    /**
     * 릴리스 검증용 테스트 토큰 발급.
     * AUTH_TEST_SECRET 환경변수가 설정되어 있고, 요청 secret이 일치할 때만 동작한다.
     */
    @Transactional
    public SignInResult issueTestToken(String secret, Locale locale) {
        String testSecret = System.getenv("AUTH_TEST_SECRET");
        if (testSecret == null || testSecret.isBlank() || !testSecret.equals(secret)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String testSocialId = "test-verification-user";
        var existing = socialAccountRepository.findByProviderAndSocialId(SocialProvider.KAKAO, testSocialId);

        User user;
        boolean isNewUser;
        if (existing.isPresent()) {
            user = userRepository.findById(existing.get().getUserId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.LINKED_USER_NOT_FOUND));
            isNewUser = false;
        } else {
            SocialIdentity identity = new SocialIdentity(SocialProvider.KAKAO, testSocialId, "test@roundtrip.com");
            user = registerNewUser(identity, locale);
            socialAccountRepository.save(UserSocialAccount.link(user.getId(), SocialProvider.KAKAO, testSocialId));
            collectionService.createDefaultCollection(user.getId());
            isNewUser = true;
        }

        IssuedTokens tokens = jwtTokenProvider.issuePair(user.getId());
        refreshTokenStore.save(user.getId(), tokens.refreshJti(), jwtTokenProvider.refreshExpiry());
        return new SignInResult(user, tokens, isNewUser);
    }

    private User registerNewUser(SocialIdentity identity, Locale clientLocale) {
        String localeTag = resolveLocale(clientLocale);
        Email email = identity.email() == null ? null : new Email(identity.email());
        Nickname nickname = new Nickname(nicknameGenerator.generate());
        String avatarUrl = RoboHashAvatar.from(nickname.value());

        User user = User.register(email, nickname, avatarUrl, localeTag, UNKNOWN_REGION);
        return userRepository.save(user);
    }

    private String resolveLocale(Locale clientLocale) {
        if (clientLocale == null) {
            return DEFAULT_LOCALE;
        }
        String tag = clientLocale.toLanguageTag();
        return (tag == null || tag.isBlank() || "und".equals(tag)) ? DEFAULT_LOCALE : tag;
    }
}
