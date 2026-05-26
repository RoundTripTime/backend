package roundtrip.user.domain.service;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum AnimalAsset {

    FOX("여우", "fox"),
    WHALE("고래", "whale"),
    LION("사자", "lion"),
    RABBIT("토끼", "rabbit"),
    TIGER("호랑이", "tiger"),
    RACCOON("너구리", "raccoon"),
    OTTER("수달", "otter"),
    PANDA("판다", "panda"),
    KOALA("코알라", "koala"),
    EAGLE("독수리", "eagle"),
    CHIPMUNK("다람쥐", "chipmunk"),
    SEA_OTTER("해달", "sea_otter"),
    CAT("고양이", "cat"),
    DOG("강아지", "dog"),
    OWL("올빼미", "owl"),
    TURTLE("거북이", "turtle"),
    PENGUIN("팽귄", "penguin"),
    DEER("사슴", "deer"),
    HAMSTER("햄스터", "hamster"),
    BEAR("곰", "bear");

    private static final Map<String, AnimalAsset> BY_KOREAN = Stream.of(values())
            .collect(Collectors.toMap(a -> a.korean, a -> a));

    private final String korean;
    private final String resourceName;

    AnimalAsset(String korean, String resourceName) {
        this.korean = korean;
        this.resourceName = resourceName;
    }

    public static boolean contains(String korean) {
        return BY_KOREAN.containsKey(korean);
    }

    public static AnimalAsset of(String korean) {
        AnimalAsset asset = BY_KOREAN.get(korean);
        if (asset == null) {
            throw new IllegalArgumentException("Unknown animal: " + korean);
        }
        return asset;
    }

    public String korean() {
        return korean;
    }

    public String resourcePath() {
        return "avatars/animals/" + resourceName + ".svg";
    }
}
