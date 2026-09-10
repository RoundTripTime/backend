package roundtrip.common.observability;

/**
 * Prometheus {@code result} 라벨에 넣는 값. 카디널리티를 묶기 위해 이 집합만 허용한다.
 */
public enum AiProviderResult {
    SUCCESS("success"),
    TIMEOUT("timeout"),
    RATE_LIMITED("rate_limited"),
    CLIENT_ERROR("client_error"),
    SERVER_ERROR("server_error"),
    CIRCUIT_OPEN("circuit_open"),
    FALLBACK("fallback"),
    UNKNOWN_ERROR("unknown_error");

    private final String label;

    AiProviderResult(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
