package roundtrip.sourcelink.infrastructure.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SupadataMetadataResponse(
        String platform,
        String type,
        String id,
        String url,
        String title,
        String description,
        List<String> tags
) {
}
