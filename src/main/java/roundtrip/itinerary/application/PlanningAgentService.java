package roundtrip.itinerary.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;
import roundtrip.common.infrastructure.FeatherlessAiRateLimiter;
import roundtrip.common.infrastructure.FeatherlessAiResponseSanitizer;
import roundtrip.itinerary.domain.entity.Itinerary;
import roundtrip.itinerary.domain.entity.ItineraryItem;
import roundtrip.itinerary.domain.repository.ItineraryRepository;
import roundtrip.place.application.PlaceService;
import roundtrip.place.domain.entity.Place;
import roundtrip.place.domain.repository.PlaceRepository;
import roundtrip.sourcelink.infrastructure.external.FeatherlessAiProperties;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class PlanningAgentService {

    private static final int MAX_TOOL_ROUNDS = 5;

    private static final String SYSTEM_PROMPT = """
            당신은 여행 일정 플래닝 어시스턴트입니다. 사용자의 요청에 따라 도구를 사용해 일정을 관리합니다.

            규칙:
            1. 도구를 사용할 때는 반드시 필요한 파라미터를 정확히 전달하세요.
            2. 여러 도구를 순차적으로 사용해야 할 경우, 한 번에 하나씩 호출하세요.
            3. 도구 실행 결과를 바탕으로 사용자에게 자연스러운 한국어로 응답하세요.
            4. 장소 추가/삭제/순서변경 등 일정 변경 시, 변경 내용을 간결하게 안내하세요.
            5. 사용자가 모호한 요청을 하면 명확히 되물어보세요.
            /no_think""";

    private static final String TOOLS_JSON = """
            [
              {
                "type": "function",
                "function": {
                  "name": "search_similar_places",
                  "description": "기준 장소와 유사한 장소를 pgvector 임베딩 기반으로 검색합니다.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "place_id": { "type": "string", "description": "기준 장소 UUID" },
                      "limit": { "type": "integer", "description": "반환할 최대 장소 수 (기본 5)" }
                    },
                    "required": ["place_id"]
                  }
                }
              },
              {
                "type": "function",
                "function": {
                  "name": "search_places_by_category",
                  "description": "카테고리와 키워드로 장소를 검색합니다.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "query": { "type": "string", "description": "검색 키워드" }
                    },
                    "required": ["query"]
                  }
                }
              },
              {
                "type": "function",
                "function": {
                  "name": "add_place_to_itinerary",
                  "description": "장소를 현재 일정에 추가합니다.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "place_id": { "type": "string", "description": "추가할 장소 UUID" },
                      "day_index": { "type": "integer", "description": "일차 (1부터 시작, 미지정 시 미배치)" },
                      "sort_order": { "type": "integer", "description": "해당 일차 내 순서" }
                    },
                    "required": ["place_id"]
                  }
                }
              },
              {
                "type": "function",
                "function": {
                  "name": "remove_place_from_itinerary",
                  "description": "일정에서 장소를 제거합니다.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "item_id": { "type": "string", "description": "제거할 일정 항목 UUID" }
                    },
                    "required": ["item_id"]
                  }
                }
              },
              {
                "type": "function",
                "function": {
                  "name": "reorder_itinerary",
                  "description": "일정의 순서를 재배열합니다. 동선 최적화나 순서 변경 시 사용합니다.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "items": {
                        "type": "array",
                        "description": "재배열할 항목 목록",
                        "items": {
                          "type": "object",
                          "properties": {
                            "item_id": { "type": "string", "description": "항목 UUID" },
                            "day_index": { "type": "integer", "description": "일차" },
                            "sort_order": { "type": "integer", "description": "순서" }
                          },
                          "required": ["item_id", "day_index", "sort_order"]
                        }
                      }
                    },
                    "required": ["items"]
                  }
                }
              },
              {
                "type": "function",
                "function": {
                  "name": "get_place_info",
                  "description": "장소의 상세 정보를 조회합니다.",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "place_id": { "type": "string", "description": "장소 UUID" }
                    },
                    "required": ["place_id"]
                  }
                }
              },
              {
                "type": "function",
                "function": {
                  "name": "summarize_itinerary",
                  "description": "현재 일정의 요약 정보를 반환합니다.",
                  "parameters": {
                    "type": "object",
                    "properties": {}
                  }
                }
              }
            ]""";

    private final ItineraryRepository itineraryRepository;
    private final PlaceRepository placeRepository;
    private final PlaceService placeService;
    private final FeatherlessAiProperties properties;
    private final ObjectMapper objectMapper;
    private final FeatherlessAiRateLimiter rateLimiter;
    private final RestClient agentRestClient;

    public PlanningAgentService(ItineraryRepository itineraryRepository,
                                PlaceRepository placeRepository,
                                PlaceService placeService,
                                FeatherlessAiProperties properties,
                                ObjectMapper objectMapper,
                                FeatherlessAiRateLimiter rateLimiter) {
        this.itineraryRepository = itineraryRepository;
        this.placeRepository = placeRepository;
        this.placeService = placeService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
        this.agentRestClient = RestClient.builder()
                .baseUrl("https://api.featherless.ai/v1")
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public AgentResponse chat(UUID userId, UUID itineraryId, String message, List<ChatMessage> history) {
        Itinerary itinerary = itineraryRepository.findByIdAndUserId(itineraryId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITINERARY_NOT_FOUND));

        String itineraryContext = buildItineraryContext(itinerary);

        // Build messages
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT + "\n\n현재 일정 정보:\n" + itineraryContext));

        if (history != null) {
            for (ChatMessage h : history) {
                messages.add(Map.of("role", h.role(), "content", h.content()));
            }
        }
        messages.add(Map.of("role", "user", "content", message));

        // Agent loop
        List<ToolResult> allToolResults = new ArrayList<>();
        boolean itineraryUpdated = false;

        if (!rateLimiter.tryAcquire(10, TimeUnit.SECONDS)) {
            return new AgentResponse("현재 다른 요청을 처리 중입니다. 잠시 후 다시 시도해주세요.", List.of(), false);
        }
        try {
            @SuppressWarnings("unchecked")
            List<Object> toolsDef = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                    .readValue(TOOLS_JSON, List.class);

            for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
                Map<String, Object> requestBody = new LinkedHashMap<>();
                requestBody.put("model", properties.model());
                requestBody.put("messages", messages);
                requestBody.put("tools", toolsDef);
                requestBody.put("temperature", 0.3);
                requestBody.put("max_tokens", 4096);

                ChatCompletionResponse response = null;
                for (int retry = 0; retry < 5; retry++) {
                    String rawResponse = agentRestClient.post()
                            .uri("/chat/completions")
                            .body(requestBody)
                            .retrieve()
                            .body(String.class);
                    log.debug("Agent round {} (retry {}): raw={}", round, retry,
                            rawResponse != null && rawResponse.length() > 500
                                    ? rawResponse.substring(0, 500) + "..." : rawResponse);
                    response = objectMapper.readValue(rawResponse, ChatCompletionResponse.class);
                    if (response != null && response.choices() != null && !response.choices().isEmpty()) {
                        var msg = response.choices().get(0).message();
                        String visibleContent = FeatherlessAiResponseSanitizer.stripThinking(msg.content());
                        if (!visibleContent.isBlank()
                                || (msg.toolCalls() != null && !msg.toolCalls().isEmpty())) {
                            break;
                        }
                    }
                    long waitMs = (retry + 1) * 5000L;
                    log.warn("Agent received empty response (model cold start), retrying ({}/5) after {}ms...", retry + 1, waitMs);
                    Thread.sleep(waitMs);
                }

                if (response == null || response.choices() == null || response.choices().isEmpty()) {
                    return new AgentResponse("죄송합니다. 응답을 생성하지 못했습니다.", List.of(), false);
                }

                var choice = response.choices().get(0);
                String content = FeatherlessAiResponseSanitizer.stripThinking(choice.message().content());
                List<ToolCall> toolCalls = choice.message().toolCalls();
                log.debug("Agent round {}: content={}, toolCalls={}", round,
                        content.length() > 200 ? content.substring(0, 200) + "..." : content,
                        toolCalls != null ? toolCalls.size() : "null");

                // Qwen3 fallback: parse <tool_call> from content if tool_calls field is empty
                if ((toolCalls == null || toolCalls.isEmpty()) && content.contains("<tool_call>")) {
                    toolCalls = parseToolCallsFromContent(content);
                    content = content.replaceAll("<tool_call>[\\s\\S]*?</tool_call>", "").trim();
                }

                // If no tool calls, return the final text response
                if (toolCalls == null || toolCalls.isEmpty()) {
                    return new AgentResponse(content, allToolResults, itineraryUpdated);
                }

                // Add assistant message with tool calls (OpenAI format)
                List<Map<String, Object>> toolCallMaps = toolCalls.stream().map(tc -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", tc.id());
                    m.put("type", "function");
                    m.put("function", Map.of("name", tc.function().name(), "arguments", tc.function().arguments()));
                    return m;
                }).toList();
                Map<String, Object> assistantMsg = new LinkedHashMap<>();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", content.isEmpty() ? null : content);
                assistantMsg.put("tool_calls", toolCallMaps);
                messages.add(assistantMsg);

                // Execute each tool call
                for (var toolCall : toolCalls) {
                    String toolName = toolCall.function().name();
                    String argsJson = toolCall.function().arguments();
                    var args = objectMapper.readTree(argsJson);

                    ToolExecutionResult execResult = executeTool(toolName, args, userId, itineraryId);

                    if (execResult.modifiesItinerary()) {
                        itineraryUpdated = true;
                    }
                    if (execResult.toolResult() != null) {
                        allToolResults.add(execResult.toolResult());
                    }

                    // Add tool result message (OpenAI format requires name field)
                    Map<String, Object> toolMsg = new LinkedHashMap<>();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", toolCall.id());
                    toolMsg.put("name", toolName);
                    toolMsg.put("content", execResult.resultJson());
                    messages.add(toolMsg);
                }
            }

            // Max rounds exceeded
            return new AgentResponse("요청을 처리했습니다.", allToolResults, itineraryUpdated);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Planning Agent error for itineraryId={}: {}", itineraryId, e.getMessage(), e);
            return new AgentResponse("죄송합니다. 요청 처리 중 오류가 발생했습니다.", List.of(), false);
        } finally {
            rateLimiter.release();
        }
    }

    private ToolExecutionResult executeTool(String toolName, tools.jackson.databind.JsonNode args,
                                             UUID userId, UUID itineraryId) {
        try {
            return switch (toolName) {
                case "search_similar_places" -> executeSearchSimilar(args);
                case "search_places_by_category" -> executeSearchByCategory(args);
                case "add_place_to_itinerary" -> executeAddPlace(args, userId, itineraryId);
                case "remove_place_from_itinerary" -> executeRemovePlace(args, userId, itineraryId);
                case "reorder_itinerary" -> executeReorder(args, userId, itineraryId);
                case "get_place_info" -> executeGetPlaceInfo(args);
                case "summarize_itinerary" -> executeSummarize(userId, itineraryId);
                default -> new ToolExecutionResult("{\"error\": \"unknown tool\"}", false, null);
            };
        } catch (Exception e) {
            log.warn("Tool execution failed: tool={}, error={}", toolName, e.getMessage());
            return new ToolExecutionResult("{\"error\": \"" + e.getMessage() + "\"}", false, null);
        }
    }

    private ToolExecutionResult executeSearchSimilar(tools.jackson.databind.JsonNode args) throws Exception {
        UUID placeId = UUID.fromString(args.get("place_id").textValue());
        int limit = args.has("limit") ? args.get("limit").intValue() : 5;

        var results = placeRepository.findSimilarPlacesRanked(placeId, limit);
        List<Map<String, Object>> places = results.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("place_id", r.id().toString());
            m.put("canonical_name", r.canonicalName());
            m.put("category", r.category() != null ? r.category().name().toLowerCase() : null);
            m.put("latitude", r.latitude());
            m.put("longitude", r.longitude());
            m.put("similarity_score", r.similarityScore());
            return m;
        }).toList();

        String json = objectMapper.writeValueAsString(Map.of("places", places));
        return new ToolExecutionResult(json, false,
                new ToolResult("search_similar_places", places));
    }

    private ToolExecutionResult executeSearchByCategory(tools.jackson.databind.JsonNode args) throws Exception {
        String query = args.get("query").textValue();

        List<Place> searchResults = placeService.searchPlaces(query, "kakao");
        List<Map<String, Object>> places = new ArrayList<>();
        for (Place p : searchResults) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("place_id", p.getId().toString());
            m.put("canonical_name", p.getCanonicalName());
            m.put("category", p.getCategory() != null ? p.getCategory().name().toLowerCase() : null);
            m.put("latitude", p.getLatitude());
            m.put("longitude", p.getLongitude());
            places.add(m);
        }

        String json = objectMapper.writeValueAsString(Map.of("places", places));
        return new ToolExecutionResult(json, false,
                new ToolResult("search_places_by_category", places));
    }

    private ToolExecutionResult executeAddPlace(tools.jackson.databind.JsonNode args,
                                                 UUID userId, UUID itineraryId) throws Exception {
        UUID placeId = UUID.fromString(args.get("place_id").textValue());
        Integer dayIndex = args.has("day_index") ? args.get("day_index").intValue() : null;
        Integer sortOrder = args.has("sort_order") ? args.get("sort_order").intValue() : null;

        itineraryRepository.findByIdAndUserId(itineraryId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITINERARY_NOT_FOUND));
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));

        ItineraryItem item = ItineraryItem.create(itineraryId, placeId, dayIndex, sortOrder, null, null);
        item = itineraryRepository.saveItem(item);

        String json = objectMapper.writeValueAsString(Map.of(
                "status", "added",
                "item_id", item.getId().toString(),
                "place_name", place.getCanonicalName()
        ));
        return new ToolExecutionResult(json, true, null);
    }

    private ToolExecutionResult executeRemovePlace(tools.jackson.databind.JsonNode args,
                                                    UUID userId, UUID itineraryId) throws Exception {
        UUID itemId = UUID.fromString(args.get("item_id").textValue());

        itineraryRepository.findByIdAndUserId(itineraryId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITINERARY_NOT_FOUND));
        ItineraryItem item = itineraryRepository.findItemByIdAndItineraryId(itemId, itineraryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITINERARY_ITEM_NOT_FOUND));

        // Get place name before deletion
        String placeName = placeRepository.findById(item.getPlaceId())
                .map(Place::getCanonicalName).orElse("알 수 없는 장소");

        itineraryRepository.deleteItemById(itemId);

        String json = objectMapper.writeValueAsString(Map.of(
                "status", "removed",
                "place_name", placeName
        ));
        return new ToolExecutionResult(json, true, null);
    }

    private ToolExecutionResult executeReorder(tools.jackson.databind.JsonNode args,
                                                UUID userId, UUID itineraryId) throws Exception {
        itineraryRepository.findByIdAndUserId(itineraryId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITINERARY_NOT_FOUND));

        var itemsNode = args.get("items");
        List<ItineraryItem> allItems = itineraryRepository.findItemsByItineraryId(itineraryId);
        Map<UUID, ItineraryItem> itemMap = new LinkedHashMap<>();
        for (ItineraryItem item : allItems) {
            itemMap.put(item.getId(), item);
        }

        for (var entry : itemsNode) {
            UUID itemId = UUID.fromString(entry.get("item_id").textValue());
            int dayIndex = entry.get("day_index").intValue();
            int sortOrder = entry.get("sort_order").intValue();

            ItineraryItem item = itemMap.get(itemId);
            if (item != null) {
                item.reorder(dayIndex, sortOrder);
            }
        }
        itineraryRepository.saveAllItems(allItems);

        String json = objectMapper.writeValueAsString(Map.of(
                "status", "reordered",
                "item_count", allItems.size()
        ));
        return new ToolExecutionResult(json, true, null);
    }

    private ToolExecutionResult executeGetPlaceInfo(tools.jackson.databind.JsonNode args) throws Exception {
        UUID placeId = UUID.fromString(args.get("place_id").textValue());
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("place_id", place.getId().toString());
        info.put("canonical_name", place.getCanonicalName());
        info.put("category", place.getCategory() != null ? place.getCategory().name().toLowerCase() : null);
        info.put("latitude", place.getLatitude());
        info.put("longitude", place.getLongitude());
        info.put("country_code", place.getCountryCode());
        info.put("kakao_place_id", place.getKakaoPlaceId());

        String json = objectMapper.writeValueAsString(info);
        return new ToolExecutionResult(json, false, null);
    }

    private ToolExecutionResult executeSummarize(UUID userId, UUID itineraryId) throws Exception {
        Itinerary itinerary = itineraryRepository.findByIdAndUserId(itineraryId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITINERARY_NOT_FOUND));
        List<ItineraryItem> items = itineraryRepository.findItemsByItineraryId(itineraryId);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("title", itinerary.getTitle());
        summary.put("destination", itinerary.getDestinationRegion());
        summary.put("start_date", itinerary.getStartDate().toString());
        summary.put("end_date", itinerary.getEndDate().toString());
        summary.put("party_size", itinerary.getPartySize());
        summary.put("total_places", items.size());

        Map<Integer, List<Map<String, String>>> days = new LinkedHashMap<>();
        for (ItineraryItem item : items) {
            int day = item.getDayIndex() != null ? item.getDayIndex() : 0;
            String placeName = placeRepository.findById(item.getPlaceId())
                    .map(Place::getCanonicalName).orElse("알 수 없음");
            days.computeIfAbsent(day, k -> new ArrayList<>())
                    .add(Map.of(
                            "item_id", item.getId().toString(),
                            "place_name", placeName,
                            "sort_order", String.valueOf(item.getSortOrder() != null ? item.getSortOrder() : 0)
                    ));
        }
        summary.put("days", days);

        String json = objectMapper.writeValueAsString(summary);
        return new ToolExecutionResult(json, false, null);
    }

    private String buildItineraryContext(Itinerary itinerary) {
        List<ItineraryItem> items = itineraryRepository.findItemsByItineraryId(itinerary.getId());
        StringBuilder sb = new StringBuilder();
        sb.append("제목: ").append(itinerary.getTitle()).append("\n");
        sb.append("목적지: ").append(itinerary.getDestinationRegion()).append("\n");
        sb.append("기간: ").append(itinerary.getStartDate()).append(" ~ ").append(itinerary.getEndDate()).append("\n");
        sb.append("인원: ").append(itinerary.getPartySize()).append("명\n");
        sb.append("장소 수: ").append(items.size()).append("개\n");

        if (!items.isEmpty()) {
            sb.append("\n일정 항목:\n");
            for (ItineraryItem item : items) {
                String placeName = placeRepository.findById(item.getPlaceId())
                        .map(Place::getCanonicalName).orElse("알 수 없음");
                sb.append("- [").append(item.getId()).append("] ");
                if (item.getDayIndex() != null) {
                    sb.append("Day ").append(item.getDayIndex()).append(" / ");
                }
                sb.append(placeName).append("\n");
            }
        }
        return sb.toString();
    }

    private List<ToolCall> parseToolCallsFromContent(String content) {
        List<ToolCall> toolCalls = new ArrayList<>();
        int idx = 0;
        while (true) {
            int start = content.indexOf("<tool_call>", idx);
            if (start == -1) break;
            int end = content.indexOf("</tool_call>", start);
            if (end == -1) break;

            String toolCallJson = content.substring(start + "<tool_call>".length(), end).trim();
            try {
                var node = objectMapper.readTree(toolCallJson);
                String name = node.has("name") ? node.get("name").textValue() : "";
                String arguments = node.has("arguments") ? objectMapper.writeValueAsString(node.get("arguments")) : "{}";
                toolCalls.add(new ToolCall(
                        "call_" + UUID.randomUUID().toString().substring(0, 8),
                        "function",
                        new FunctionCall(name, arguments)
                ));
            } catch (Exception e) {
                log.warn("Failed to parse <tool_call> from content: {}", e.getMessage());
            }
            idx = end + "</tool_call>".length();
        }
        return toolCalls;
    }

    // ── DTOs ──

    public record ChatMessage(String role, String content) {}

    public record AgentResponse(
            String reply,
            List<ToolResult> toolResults,
            boolean itineraryUpdated
    ) {}

    public record ToolResult(
            String tool,
            List<Map<String, Object>> places
    ) {}

    record ToolExecutionResult(String resultJson, boolean modifiesItinerary, ToolResult toolResult) {}

    // ── API Response Records ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatCompletionResponse(List<Choice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String role, String content,
                   @com.fasterxml.jackson.annotation.JsonProperty("tool_calls") List<ToolCall> toolCalls) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ToolCall(String id, String type, FunctionCall function) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FunctionCall(String name, String arguments) {}
}
