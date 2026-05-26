package roundtrip.sourcelink.infrastructure.external;

public record PlaceParseResult(
        String name,
        String category,
        double confidence,
        String evidence
) {
}
