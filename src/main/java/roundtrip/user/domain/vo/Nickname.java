package roundtrip.user.domain.vo;

import java.util.Objects;

public record Nickname(String value) {

    private static final int MAX_LENGTH = 50;

    public Nickname {
        Objects.requireNonNull(value, "nickname은 null일 수 없습니다");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("nickname은 비어있을 수 없습니다");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                "nickname은 " + MAX_LENGTH + "자 이내여야 합니다: " + value.length()
            );
        }
    }
}
