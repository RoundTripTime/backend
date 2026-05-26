package roundtrip.itinerary.infrastructure.myrealtrip;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "myrealtrip")
public record MyRealTripProperties(
        String apiKey,
        String baseUrl
) {
}
