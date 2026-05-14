package roundtrip.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // 공통
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // 인증
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 유효하지 않습니다."),
    INVALID_ID_TOKEN(HttpStatus.UNAUTHORIZED, "id_token이 유효하지 않습니다."),
    PROVIDER_NOT_CONFIGURED(HttpStatus.BAD_REQUEST, "소셜 로그인이 구성되지 않았습니다."),
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 provider 입니다."),
    JWKS_FETCH_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "외부 인증 키 조회에 실패했습니다."),

    // 사용자
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    LINKED_USER_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "연결된 사용자가 존재하지 않습니다."),
    ;

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
