package roundtrip.common.infrastructure;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FeatherlessAiIsolationRetryAfterTest {

    @Test
    void parseRetryAfterReadsSecondsAndIgnoresNonNumericValues() {
        assertThat(FeatherlessAiIsolation.parseRetryAfter("120")).isEqualTo(Duration.ofSeconds(120));
        assertThat(FeatherlessAiIsolation.parseRetryAfter("not-a-number")).isNull();
        assertThat(FeatherlessAiIsolation.parseRetryAfter("")).isNull();
    }
}
