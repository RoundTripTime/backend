package roundtrip.sourcelink.infrastructure.external;

public record GeminiPlaceParseResult(
        String name,
        String category,
        double confidence,
        String evidence
) {
}
