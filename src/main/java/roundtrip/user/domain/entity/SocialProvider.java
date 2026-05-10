package roundtrip.user.domain.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum SocialProvider {
    KAKAO,
    GOOGLE;

    @JsonCreator
    public static SocialProvider from(String value) {
        if (value == null) {
            return null;
        }
        return SocialProvider.valueOf(value.toUpperCase());
    }
}
