package roundtrip.user.domain.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class RoboHashAvatar {

    private static final String BASE_URL = "https://robohash.org/";
    private static final String QUERY = "?set=set4";

    private RoboHashAvatar() {}

    public static String from(String nickname) {
        return BASE_URL + URLEncoder.encode(nickname, StandardCharsets.UTF_8) + QUERY;
    }

    public static boolean isRoboHash(String avatarUrl) {
        return avatarUrl != null && avatarUrl.startsWith(BASE_URL);
    }
}
