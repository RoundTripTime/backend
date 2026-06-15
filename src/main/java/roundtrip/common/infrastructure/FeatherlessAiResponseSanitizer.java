package roundtrip.common.infrastructure;

import java.util.regex.Pattern;

public final class FeatherlessAiResponseSanitizer {

    private static final Pattern THINKING_BLOCK = Pattern.compile(
            "<(think|reasoning)\\b[^>]*>[\\s\\S]*?</\\1\\s*>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern THINKING_TAG = Pattern.compile(
            "</?(?:think|reasoning)\\b[^>]*>",
            Pattern.CASE_INSENSITIVE
    );

    private FeatherlessAiResponseSanitizer() {
    }

    public static String stripThinking(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String sanitized = THINKING_BLOCK.matcher(content).replaceAll("");
        return THINKING_TAG.matcher(sanitized).replaceAll("").trim();
    }
}
