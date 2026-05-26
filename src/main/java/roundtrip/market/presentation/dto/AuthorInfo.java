package roundtrip.market.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.user.domain.entity.User;

import java.util.UUID;

@Schema(description = "작성자 정보")
public record AuthorInfo(
    @Schema(description = "작성자 고유 ID") UUID userId,
    @Schema(description = "작성자 닉네임") String nickname,
    @Schema(description = "작성자 프로필 이미지 URL") String avatarUrl
) {
    public static AuthorInfo from(User user) {
        if (user == null) return null;
        return new AuthorInfo(user.getId(), user.getNickname().value(), user.getAvatarUrl());
    }
}
