package roundtrip.place.domain.entity;

public record ThumbnailImage(
        String url,
        String attribution,
        String license,
        String licenseUrl,
        String sourceUrl
) {
}
