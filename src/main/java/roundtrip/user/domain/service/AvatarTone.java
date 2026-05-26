package roundtrip.user.domain.service;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum AvatarTone {

    JOYFUL(
        new String[]{"행복한", "활발한", "쾌활한", "장난꾸러기", "엉뚱한"},
        "#FF6B35", "#FFD23F",
        "warm-glow",
        "sparkle"
    ),
    SERENE(
        new String[]{"조용한", "느긋한", "온화한", "다정한", "우아한"},
        "#74B9FF", "#A8E6CF",
        "soft-blur",
        "cloud"
    ),
    BOLD(
        new String[]{"용감한", "씩씩한", "당당한", "재빠른", "현명한"},
        "#E17055", "#D63031",
        "sharp-shadow",
        "lightning"
    ),
    DREAMY(
        new String[]{"졸린", "수줍은", "이상한", "신비로운", "사랑스러운"},
        "#A29BFE", "#DDA0DD",
        "dreamy-glow",
        "zzz"
    );

    private static final Map<String, AvatarTone> BY_ADJECTIVE;

    static {
        BY_ADJECTIVE = Stream.of(values())
                .flatMap(tone -> Stream.of(tone.adjectives).map(adj -> Map.entry(adj, tone)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private final String[] adjectives;
    private final String gradientStart;
    private final String gradientEnd;
    private final String filterType;
    private final String overlayType;

    AvatarTone(String[] adjectives, String gradientStart, String gradientEnd,
               String filterType, String overlayType) {
        this.adjectives = adjectives;
        this.gradientStart = gradientStart;
        this.gradientEnd = gradientEnd;
        this.filterType = filterType;
        this.overlayType = overlayType;
    }

    public static boolean contains(String adjective) {
        return BY_ADJECTIVE.containsKey(adjective);
    }

    public static AvatarTone of(String adjective) {
        AvatarTone tone = BY_ADJECTIVE.get(adjective);
        if (tone == null) {
            throw new IllegalArgumentException("Unknown adjective: " + adjective);
        }
        return tone;
    }

    public String gradientStart() { return gradientStart; }
    public String gradientEnd() { return gradientEnd; }
    public String filterType() { return filterType; }
    public String overlayType() { return overlayType; }
}
