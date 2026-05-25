package roundtrip.user.domain.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoboHashAvatarTest {

    @Test
    void from_generatesUrlWithSet4() {
        String url = RoboHashAvatar.from("이상한 여우 8237");

        assertThat(url).startsWith("https://robohash.org/");
        assertThat(url).endsWith("?set=set4");
        assertThat(url).contains("%EC%9D%B4%EC%83%81%ED%95%9C"); // URL-encoded 한글
    }

    @Test
    void from_encodesSpaces() {
        String url = RoboHashAvatar.from("용감한 고래 1234");

        assertThat(url).doesNotContain(" ");
        assertThat(url.contains("+") || url.contains("%20")).isTrue();
    }

    @Test
    void isRoboHash_roboHashUrl_returnsTrue() {
        String url = RoboHashAvatar.from("테스트닉네임");

        assertThat(RoboHashAvatar.isRoboHash(url)).isTrue();
    }

    @Test
    void isRoboHash_s3Url_returnsFalse() {
        assertThat(RoboHashAvatar.isRoboHash("https://my-bucket.s3.ap-northeast-2.amazonaws.com/avatars/abc.jpg"))
                .isFalse();
    }

    @Test
    void isRoboHash_null_returnsFalse() {
        assertThat(RoboHashAvatar.isRoboHash(null)).isFalse();
    }
}
