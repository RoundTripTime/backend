package roundtrip.user.domain;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;

@Component
public class NicknameGenerator {

    private static final List<String> ADJECTIVES = List.of(
        "이상한", "용감한", "조용한", "행복한", "졸린", "활발한", "수줍은", "엉뚱한",
        "우아한", "씩씩한", "느긋한", "사랑스러운", "장난꾸러기", "현명한", "재빠른", "신비로운",
        "다정한", "쾌활한", "온화한", "당당한"
    );

    private static final List<String> ANIMALS = List.of(
        "여우", "고래", "사자", "토끼", "호랑이", "너구리", "수달", "판다",
        "코알라", "독수리", "다람쥐", "해달", "고양이", "강아지", "올빼미", "거북이",
        "팽귄", "사슴", "햄스터", "곰"
    );

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        var adjective = ADJECTIVES.get(random.nextInt(ADJECTIVES.size()));
        var animal = ANIMALS.get(random.nextInt(ANIMALS.size()));
        var number = String.format("%04d", random.nextInt(10_000));
        return adjective + " " + animal + " " + number;
    }
}
