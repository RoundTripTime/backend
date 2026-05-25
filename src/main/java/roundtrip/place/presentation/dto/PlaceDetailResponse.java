package roundtrip.place.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.place.application.PlaceService.PlaceDetailResult;
import roundtrip.place.domain.entity.Place;
import roundtrip.sourcelink.domain.entity.SourceLink;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "장소 상세 응답")
public record PlaceDetailResponse(
    @Schema(description = "장소 고유 ID")
    UUID placeId,
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
    @Schema(description = "Google Maps 장소 ID", example = "ChIJ...")
    String googlePlaceId,
    @Schema(description = "Kakao Maps 장소 ID", example = "12345678")
    String kakaoPlaceId,
    @Schema(description = "대표 썸네일 이미지 URL")
    String thumbnailUrl,
    @Schema(description = "썸네일 출처", example = "flickr",
        allowableValues = {"flickr", "wikimedia", "google_places"})
    String thumbnailSource,
    @Schema(description = "이 장소가 추출된 원본 영상 정보")
    PlaceSourceLinkInfo sourceLink,
    @Schema(description = "장소 추출 근거 텍스트", example = "캡션: 시부야 교차로 최고의 뷰포인트")
    String evidence,
    @Schema(description = "장소 저장 일시 (ISO 8601)", example = "2024-12-01T10:00:20Z")
    OffsetDateTime createdAt
) {
    public static PlaceDetailResponse from(PlaceDetailResult result) {
        Place p = result.place();
        SourceLink sl = result.sourceLink();
        return new PlaceDetailResponse(
                p.getId(),
                p.getCanonicalName(),
                p.getLatitude(),
                p.getLongitude(),
                p.getCategory() != null ? p.getCategory().name().toLowerCase() : null,
                p.getCountryCode(),
                p.getGooglePlaceId(),
                p.getKakaoPlaceId(),
                p.getThumbnailUrl(),
                p.getThumbnailSource() != null ? p.getThumbnailSource().name().toLowerCase() : null,
                PlaceSourceLinkInfo.from(sl),
                p.getEvidence(),
                p.getCreatedAt()
        );
    }
}
