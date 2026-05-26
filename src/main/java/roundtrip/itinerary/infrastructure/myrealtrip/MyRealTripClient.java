package roundtrip.itinerary.infrastructure.myrealtrip;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MyRealTripClient {

    private final RestClient restClient;

    public MyRealTripClient(MyRealTripProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * 마이링크 생성 — 마이리얼트립 URL을 단축 어필리에이트 링크로 변환
     */
    public MyLinkResponse createMyLink(String targetUrl) {
        log.debug("Creating MyRealTrip mylink for targetUrl={}", targetUrl);
        var body = Map.of("targetUrl", targetUrl);
        var response = restClient.post()
                .uri("/v1/mylink")
                .body(body)
                .retrieve()
                .body(MrtApiResponse.class);
        if (response != null && response.data() != null) {
            return response.data();
        }
        throw new RuntimeException("마이리얼트립 마이링크 생성 실패: targetUrl=" + targetUrl);
    }

    /**
     * 항공 운임 조회 랜딩 URL 생성
     */
    public String createFlightLandingUrl(String depAirportCd, String arrAirportCd,
                                         String tripTypeCd, String depDate, String arrDate,
                                         int adult) {
        log.debug("Creating flight landing URL: {} → {}", depAirportCd, arrAirportCd);
        var body = new java.util.HashMap<String, Object>();
        body.put("depAirportCd", depAirportCd);
        body.put("arrAirportCd", arrAirportCd);
        body.put("tripTypeCd", tripTypeCd);
        body.put("depDate", depDate);
        if (arrDate != null) body.put("arrDate", arrDate);
        body.put("adult", adult);
        body.put("child", 0);
        body.put("infant", 0);

        var response = restClient.post()
                .uri("/v1/products/flight/fare-query-landing-url")
                .body(body)
                .retrieve()
                .body(MrtStringDataResponse.class);
        if (response != null && response.data() != null) {
            return response.data();
        }
        throw new RuntimeException("마이리얼트립 항공 랜딩 URL 생성 실패");
    }

    /**
     * 숙소 검색
     */
    public AccommodationSearchResponse searchAccommodation(String keyword, String checkIn,
                                                           String checkOut, int adultCount) {
        log.debug("Searching accommodation: keyword={}, checkIn={}", keyword, checkIn);
        var body = Map.of(
                "keyword", keyword,
                "checkIn", checkIn,
                "checkOut", checkOut,
                "adultCount", adultCount,
                "childCount", 0,
                "page", 0,
                "size", 5
        );
        var response = restClient.post()
                .uri("/v1/products/accommodation/search")
                .body(body)
                .retrieve()
                .body(MrtAccommodationResponse.class);
        if (response != null && response.data() != null) {
            return response.data();
        }
        return new AccommodationSearchResponse(List.of(), 0, 0, 0);
    }

    /**
     * 투어/티켓/액티비티 검색
     */
    public TnaSearchResponse searchTna(String keyword, String city) {
        log.debug("Searching TNA: keyword={}, city={}", keyword, city);
        var body = new java.util.HashMap<String, Object>();
        body.put("keyword", keyword);
        if (city != null) body.put("city", city);
        body.put("page", 1);
        body.put("perPage", 5);

        var response = restClient.post()
                .uri("/v1/products/tna/search")
                .body(body)
                .retrieve()
                .body(MrtTnaResponse.class);
        if (response != null && response.data() != null) {
            return response.data();
        }
        return new TnaSearchResponse(List.of(), 0, 1, 5, false);
    }

    // ──────────────────── Response DTOs ────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MrtApiResponse(MyLinkResponse data, MrtResult result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MrtStringDataResponse(String data, MrtResult result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MrtAccommodationResponse(AccommodationSearchResponse data, MrtResult result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MrtTnaResponse(TnaSearchResponse data, MrtResult result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MrtResult(int status, String message, String code) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MyLinkResponse(String mylink, Long mylinkId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AccommodationSearchResponse(
            List<AccommodationItem> items,
            int totalCount,
            int page,
            int size
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AccommodationItem(
            Long itemId,
            String itemName,
            Long salePrice,
            Long originalPrice,
            Integer starRating,
            String reviewScore,
            Integer reviewCount,
            String imageUrl
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TnaSearchResponse(
            List<TnaItem> items,
            int totalCount,
            int page,
            int perPage,
            boolean hasNextPage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TnaItem(
            String gid,
            String itemName,
            String description,
            Long salePrice,
            String priceDisplay,
            String category,
            Double reviewScore,
            Integer reviewCount,
            String imageUrl,
            String productUrl,
            String deepLink,
            List<String> tags
    ) {}
}
