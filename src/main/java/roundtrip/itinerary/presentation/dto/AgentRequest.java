package roundtrip.itinerary.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

@Schema(description = "Planning Agent 메시지 요청")
public record AgentRequest(
    @NotBlank
    @Schema(description = "사용자 입력 메시지", example = "비슷한 카페 찾아줘")
    String message,

    @Schema(description = "세션 내 대화 이력. 클라이언트가 유지하며 매 요청마다 전달")
    List<HistoryEntry> history
) {
    @Schema(description = "대화 이력 항목")
    public record HistoryEntry(
        @Schema(description = "역할 (user / assistant)", example = "user")
        String role,
        @Schema(description = "메시지 내용", example = "동선 최적화해줘")
        String content
    ) {}
}
