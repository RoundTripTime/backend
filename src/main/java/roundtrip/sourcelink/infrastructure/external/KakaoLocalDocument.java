package roundtrip.sourcelink.infrastructure.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoLocalDocument(
        String id,
        @JsonProperty("place_name") String placeName,
        @JsonProperty("category_group_code") String categoryGroupCode,
        String x,
        String y,
        @JsonProperty("address_name") String addressName,
        @JsonProperty("road_address_name") String roadAddressName
) {
}
