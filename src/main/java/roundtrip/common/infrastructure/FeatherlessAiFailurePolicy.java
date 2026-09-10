package roundtrip.common.infrastructure;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

/**
 * FeatherlessAI 재시도와 서킷에 넣을 실패만 고른다.
 * 400, 401, 403, 검증 오류는 재시도하지 않는다.
 */
public final class FeatherlessAiFailurePolicy {

    private FeatherlessAiFailurePolicy() {
    }

    public static boolean isRetryable(Throwable error, boolean retryOnTimeout) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof CallNotPermittedException) {
                return false;
            }
            if (current instanceof RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (status == 429 || status < 500) {
                    return false;
                }
                return status < 600;
            }
            if (isTimeout(current)) {
                return retryOnTimeout;
            }
            if (current instanceof ConnectException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static boolean isCircuitFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof CallNotPermittedException) {
                return false;
            }
            if (current instanceof RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                return status == 429 || status >= 500;
            }
            if (isTimeout(current) || current instanceof ConnectException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static boolean isTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException
                    || current instanceof java.io.InterruptedIOException
                    || current instanceof java.util.concurrent.CancellationException
                    || current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            if (current instanceof ResourceAccessException && containsTimeout(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean containsTimeout(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("timed out")
                || lower.contains("timeout")
                || lower.contains("request cancelled");
    }
}
