package roundtrip.auth.infrastructure.jwt;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import roundtrip.auth.domain.IssuedTokens;
import roundtrip.auth.domain.TokenType;
import roundtrip.common.exception.BusinessException;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-must-be-at-least-thirty-two-bytes-long-1234567890";

    private final JwtTokenProvider provider = new JwtTokenProvider(
        new JwtProperties(SECRET, 1_800_000L, 1_209_600_000L)
    );

    @Test
    void issuePair_round_trip_succeeds_with_distinct_jti() {
        UUID userId = UUID.randomUUID();

        IssuedTokens tokens = provider.issuePair(userId);

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.refreshJti()).isNotBlank();

        Claims access = provider.parseAndValidate(tokens.accessToken(), TokenType.ACCESS);
        Claims refresh = provider.parseAndValidate(tokens.refreshToken(), TokenType.REFRESH);

        assertThat(provider.extractUserId(access)).isEqualTo(userId);
        assertThat(provider.extractUserId(refresh)).isEqualTo(userId);
        assertThat(access.getId()).isNotEqualTo(refresh.getId());
        assertThat(provider.extractJti(refresh)).isEqualTo(tokens.refreshJti());
    }

    @Test
    void parseAndValidate_typeMismatch_throws() {
        IssuedTokens tokens = provider.issuePair(UUID.randomUUID());

        assertThatThrownBy(() -> provider.parseAndValidate(tokens.accessToken(), TokenType.REFRESH))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("토큰 타입이 올바르지 않습니다");
    }

    @Test
    void parseAndValidate_garbageToken_throws() {
        assertThatThrownBy(() -> provider.parseAndValidate("not.a.jwt", TokenType.ACCESS))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("토큰이 유효하지 않습니다");
    }

    @Test
    void parseAndValidate_expiredToken_throws() throws InterruptedException {
        JwtTokenProvider expired = new JwtTokenProvider(new JwtProperties(SECRET, 0L, 0L));
        IssuedTokens tokens = expired.issuePair(UUID.randomUUID());

        Thread.sleep(5);

        assertThatThrownBy(() -> expired.parseAndValidate(tokens.accessToken(), TokenType.ACCESS))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("토큰이 유효하지 않습니다");
    }

    @Test
    void constructor_secretShorterThan32Bytes_throws() {
        assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties("too-short", 1L, 1L)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("최소 32바이트");
    }

    @Test
    void refreshExpiry_returnsConfiguredDuration() {
        assertThat(provider.refreshExpiry()).isEqualTo(Duration.ofMillis(1_209_600_000L));
    }
}
