package roundtrip.user.domain.vo;

import java.util.Objects;
import java.util.regex.Pattern;

public record Email(String value) {

    private static final Pattern PATTERN = Pattern.compile(
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    public Email {
        Objects.requireNonNull(value, "email은 null일 수 없습니다");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("올바르지 않은 이메일 형식: " + value);
        }
    }
}
