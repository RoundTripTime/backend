package roundtrip.common.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class FeatherlessAiFailurePolicyTest {

    @Test
    void retriesServerErrorsAndConnectionFailuresButNotClientErrors() {
        assertThat(FeatherlessAiFailurePolicy.isRetryable(response(503), true)).isTrue();
        assertThat(FeatherlessAiFailurePolicy.isRetryable(response(500), true)).isTrue();
        assertThat(FeatherlessAiFailurePolicy.isRetryable(new ResourceAccessException("I/O error", new ConnectException("refused")), true)).isTrue();

        assertThat(FeatherlessAiFailurePolicy.isRetryable(response(400), true)).isFalse();
        assertThat(FeatherlessAiFailurePolicy.isRetryable(response(401), true)).isFalse();
        assertThat(FeatherlessAiFailurePolicy.isRetryable(response(403), true)).isFalse();
        assertThat(FeatherlessAiFailurePolicy.isRetryable(response(429), true)).isFalse();
    }

    @Test
    void retriesTimeoutOnlyWhenEnabled() {
        SocketTimeoutException timeout = new SocketTimeoutException("read timed out");
        assertThat(FeatherlessAiFailurePolicy.isRetryable(timeout, true)).isTrue();
        assertThat(FeatherlessAiFailurePolicy.isRetryable(timeout, false)).isFalse();
        assertThat(FeatherlessAiFailurePolicy.isRetryable(
                new ResourceAccessException("I/O error on POST request for \"http://localhost\": Request cancelled",
                        new java.io.IOException("Request cancelled")), true)).isTrue();
        assertThat(FeatherlessAiFailurePolicy.isRetryable(
                new ResourceAccessException("I/O error on POST request for \"http://localhost\": Request cancelled",
                        new java.io.IOException("Request cancelled")), false)).isFalse();
    }

    @Test
    void circuitRecordsServerTimeoutAnd429ButNotValidationErrors() {
        assertThat(FeatherlessAiFailurePolicy.isCircuitFailure(response(503))).isTrue();
        assertThat(FeatherlessAiFailurePolicy.isCircuitFailure(response(429))).isTrue();
        assertThat(FeatherlessAiFailurePolicy.isCircuitFailure(new SocketTimeoutException("read timed out"))).isTrue();
        assertThat(FeatherlessAiFailurePolicy.isCircuitFailure(response(400))).isFalse();
        assertThat(FeatherlessAiFailurePolicy.isCircuitFailure(response(403))).isFalse();
    }

    private static RestClientResponseException response(int status) {
        return new RestClientResponseException(
                "status " + status,
                HttpStatusCode.valueOf(status),
                "error",
                HttpHeaders.EMPTY,
                null,
                null
        );
    }
}
