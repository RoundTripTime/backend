package roundtrip.auth.application;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import roundtrip.auth.domain.IssuedTokens;
import roundtrip.auth.domain.SocialIdentity;
import roundtrip.auth.domain.TokenType;
import roundtrip.auth.infrastructure.jwt.JwtTokenProvider;
import roundtrip.auth.infrastructure.refresh.RefreshTokenStore;
import roundtrip.auth.infrastructure.social.SocialIdTokenVerifierRegistry;
import roundtrip.common.exception.BusinessException;
import roundtrip.user.domain.entity.SocialProvider;
import roundtrip.user.domain.entity.User;
import roundtrip.user.domain.entity.UserSocialAccount;
import roundtrip.user.domain.repository.UserRepository;
import roundtrip.user.domain.repository.UserSocialAccountRepository;
import roundtrip.user.domain.service.NicknameGenerator;
import roundtrip.user.domain.vo.Nickname;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock SocialIdTokenVerifierRegistry verifierRegistry;
    @Mock UserRepository userRepository;
    @Mock UserSocialAccountRepository socialAccountRepository;
    @Mock NicknameGenerator nicknameGenerator;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock RefreshTokenStore refreshTokenStore;

    @InjectMocks AuthService service;

    private final SocialIdentity identity = new SocialIdentity(SocialProvider.KAKAO, "social-1", "u@example.com");
    private final IssuedTokens tokens = new IssuedTokens("access", "refresh", "new-jti");

    @Test
    void signIn_newUser_registersAndIssuesTokens() {
        UUID newUserId = UUID.randomUUID();
        when(verifierRegistry.verify(SocialProvider.KAKAO, "id-token")).thenReturn(identity);
        when(socialAccountRepository.findByProviderAndSocialId(SocialProvider.KAKAO, "social-1"))
            .thenReturn(Optional.empty());
        when(nicknameGenerator.generate()).thenReturn("이상한 여우 0001");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", newUserId);
            return saved;
        });
        when(jwtTokenProvider.issuePair(newUserId)).thenReturn(tokens);
        when(jwtTokenProvider.refreshExpiry()).thenReturn(Duration.ofDays(14));

        SignInResult result = service.signInWithSocial(SocialProvider.KAKAO, "id-token", Locale.KOREA);

        assertThat(result.isNewUser()).isTrue();
        assertThat(result.tokens()).isEqualTo(tokens);
        verify(userRepository).save(any(User.class));
        verify(socialAccountRepository).save(any(UserSocialAccount.class));
        verify(refreshTokenStore).save(eq(newUserId), eq("new-jti"), any());
    }

    @Test
    void signIn_existingUser_skipsRegistration() {
        UUID existingUserId = UUID.randomUUID();
        UserSocialAccount linked = UserSocialAccount.link(existingUserId, SocialProvider.KAKAO, "social-1");
        User existing = User.register(null, new Nickname("기존"), null, "ko-KR", "South Korea");
        ReflectionTestUtils.setField(existing, "id", existingUserId);

        when(verifierRegistry.verify(SocialProvider.KAKAO, "id-token")).thenReturn(identity);
        when(socialAccountRepository.findByProviderAndSocialId(SocialProvider.KAKAO, "social-1"))
            .thenReturn(Optional.of(linked));
        when(userRepository.findById(existingUserId)).thenReturn(Optional.of(existing));
        when(jwtTokenProvider.issuePair(existingUserId)).thenReturn(tokens);
        when(jwtTokenProvider.refreshExpiry()).thenReturn(Duration.ofDays(14));

        SignInResult result = service.signInWithSocial(SocialProvider.KAKAO, "id-token", Locale.KOREA);

        assertThat(result.isNewUser()).isFalse();
        verify(userRepository, never()).save(any());
        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    void refresh_validToken_rotatesAndReturnsNewPair() {
        UUID userId = UUID.randomUUID();
        Claims claims = mock(Claims.class);
        when(jwtTokenProvider.parseAndValidate("refresh-token", TokenType.REFRESH)).thenReturn(claims);
        when(jwtTokenProvider.extractUserId(claims)).thenReturn(userId);
        when(jwtTokenProvider.extractJti(claims)).thenReturn("old-jti");
        when(refreshTokenStore.exists(userId, "old-jti")).thenReturn(true);
        when(jwtTokenProvider.issuePair(userId)).thenReturn(tokens);
        when(jwtTokenProvider.refreshExpiry()).thenReturn(Duration.ofDays(14));

        IssuedTokens issued = service.refresh("refresh-token");

        assertThat(issued).isEqualTo(tokens);
        verify(refreshTokenStore).delete(userId, "old-jti");
        verify(refreshTokenStore).save(eq(userId), eq("new-jti"), any());
    }

    @Test
    void refresh_reusedToken_clearsAllAndThrows() {
        UUID userId = UUID.randomUUID();
        Claims claims = mock(Claims.class);
        when(jwtTokenProvider.parseAndValidate("refresh-token", TokenType.REFRESH)).thenReturn(claims);
        when(jwtTokenProvider.extractUserId(claims)).thenReturn(userId);
        when(jwtTokenProvider.extractJti(claims)).thenReturn("old-jti");
        when(refreshTokenStore.exists(userId, "old-jti")).thenReturn(false);

        assertThatThrownBy(() -> service.refresh("refresh-token"))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getCode())
            .isEqualTo("INVALID_TOKEN");

        verify(refreshTokenStore).deleteAll(userId);
        verify(refreshTokenStore, never()).save(any(), any(), any());
    }

    @Test
    void logout_callsDeleteAll() {
        UUID userId = UUID.randomUUID();
        service.logout(userId);
        verify(refreshTokenStore).deleteAll(userId);
    }
}
