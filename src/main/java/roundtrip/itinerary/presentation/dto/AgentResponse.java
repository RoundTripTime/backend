package roundtrip.itinerary.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import roundtrip.itinerary.application.PlanningAgentService;

import java.util.List;
import java.util.Map;

@Schema(description = "Planning Agent 응답")
public record AgentResponse(
    @Schema(description = "Agent의 자연어 응답 텍스트")
    String reply,

    @Schema(description = "실행된 Tool별 결과 목록")
    List<ToolResultDto> toolResults,

    @Schema(description = "true면 플랜이 변경됨. GET /itineraries/:itinerary_id 재호출 필요")
    boolean itineraryUpdated
) {
    public static AgentResponse from(PlanningAgentService.AgentResponse agentResponse) {
        List<ToolResultDto> tools = agentResponse.toolResults().stream()
                .map(tr -> new ToolResultDto(tr.tool(), tr.places()))
                .toList();
        return new AgentResponse(agentResponse.reply(), tools, agentResponse.itineraryUpdated());
    }

    @Schema(description = "Tool 실행 결과")
    public record ToolResultDto(
        @Schema(description = "실행된 Tool 이름", example = "search_similar_places")
        String tool,
        @Schema(description = "유사/검색 Tool의 경우 장소 목록")
        List<Map<String, Object>> places
    ) {}
}
