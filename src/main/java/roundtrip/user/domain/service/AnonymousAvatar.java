package roundtrip.user.domain.service;

import roundtrip.user.infrastructure.s3.AvatarStorage;

public final class AnonymousAvatar {

    private static final String ANONYMOUS_PREFIX = "avatars/anonymous/";
    private static final String PLACEHOLDER_PREFIX = "https://anonymous-avatar.local/avatars/anonymous/";

    private AnonymousAvatar() {}

    /**
     * 닉네임("형용사 동물 번호")에서 형용사+동물을 파싱하여
     * S3에 캐싱된 아바타 URL을 반환한다. 캐시 미스 시 생성 후 업로드.
     * avatarStorage가 null이면 placeholder URL을 반환한다.
     * 닉네임이 "형용사 동물 번호" 형식이 아니면 null을 반환한다.
     */
    public static String resolve(String nickname, AvatarStorage avatarStorage) {
        String[] parts = nickname.split(" ");
        if (parts.length < 2) {
            return null;
        }

        String adjective = parts[0];
        String animal = parts[1];

        if (!AvatarTone.contains(adjective) || !AnimalAsset.contains(animal)) {
            return null;
        }

        String key = ANONYMOUS_PREFIX + adjective + "_" + animal + ".png";

        if (avatarStorage == null) {
            return PLACEHOLDER_PREFIX + adjective + "_" + animal + ".png";
        }

        // S3 캐시 확인
        if (avatarStorage.exists(key)) {
            return avatarStorage.buildUrl(key);
        }

        // 합성 → 렌더링 → 업로드
        AvatarTone tone = AvatarTone.of(adjective);
        AnimalAsset asset = AnimalAsset.of(animal);
        String svg = AvatarSvgComposer.compose(tone, asset);
        byte[] png = AvatarRenderer.toPng(svg);

        return avatarStorage.uploadBytes(key, png, "image/png");
    }

    /**
     * 주어진 URL이 Anonymous 아바타인지 확인.
     */
    public static boolean isAnonymous(String avatarUrl) {
        return avatarUrl != null && avatarUrl.contains(ANONYMOUS_PREFIX);
    }
}
