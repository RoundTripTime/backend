package roundtrip.user.presentation.dto;

import roundtrip.user.domain.entity.User;

import java.time.OffsetDateTime;

public record MyProfileResponse(
    String id,
    String nickname,
    String avatarUrl,
    String email,
    String locale,
    String homeRegion,
    String mapProvider,
    int creditBalance,
    OffsetDateTime createdAt
) {
    public static MyProfileResponse from(User user){
        return new MyProfileResponse(
            user.getId().toString(),
            user.getNickname().value(),
            user.getAvatarUrl(),
            user.getEmail() == null ? null : user.getEmail().value(),
            user.getLocale(),
            user.getHomeRegion(),
            user.getMapProvider().name().toLowerCase(),
            user.getCreditBalance(),
            user.getCreatedAt()
        );
    }
}
