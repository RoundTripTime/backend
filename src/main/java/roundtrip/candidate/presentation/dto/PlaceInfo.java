package roundtrip.candidate.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.place.domain.entity.Place;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "지도 정규화된 장소 정보")
public record PlaceInfo(
    @Schema(description = "장소 고유 ID")
    UUID id,
    @Schema(description = "정규화된 공식 장소명", example = "시부야 스크램블 교차로")
    String canonicalName,
    @Schema(description = "위도", example = "35.659513")
    BigDecimal latitude,
    @Schema(description = "경도", example = "139.70044")
    BigDecimal longitude,
    @Schema(description = "장소 카테고리", example = "관광명소",
        allowableValues = {"관광명소", "맛집", "카페", "숙박", "자연", "기타"})
    String category,
    @Schema(description = "국가 코드 (ISO 3166-1 alpha-2)", example = "JP")
    String countryCode,
    @Schema(description = "Kakao Maps 장소 ID", example = "12345678")
    String kakaoPlaceId,
    @Schema(description = "장소 썸네일 URL")
    String thumbnailUrl
) {
    public static PlaceInfo from(Place place) {
        if (place == null) return null;
        return new PlaceInfo(
                place.getId(),
                place.getCanonicalName(),
                place.getLatitude(),
                place.getLongitude(),
                place.getCategory() != null ? place.getCategory().name().toLowerCase() : null,
                place.getCountryCode(),
                place.getKakaoPlaceId(),
                place.getThumbnailUrl()
        );
    }
}
