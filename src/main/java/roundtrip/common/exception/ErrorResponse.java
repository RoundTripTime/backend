package roundtrip.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 에러 응답")
public record ErrorResponse(
    @Schema(description = "에러 상세")
    ErrorBody error
) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new ErrorBody(code, message));
    }

    @Schema(description = "에러 본문")
    public record ErrorBody(
        @Schema(description = "에러 코드", example = "PLACE_NOT_FOUND")
        String code,
        @Schema(description = "에러 메시지", example = "해당 장소를 찾을 수 없습니다.")
        String message
    ) {}
}
