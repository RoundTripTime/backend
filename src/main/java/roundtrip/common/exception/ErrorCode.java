package roundtrip.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // 공통
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "이미 존재하는 리소스입니다."),
    UNPROCESSABLE(HttpStatus.UNPROCESSABLE_CONTENT, "처리할 수 없는 상태입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // 인증
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 유효하지 않습니다."),
    INVALID_ID_TOKEN(HttpStatus.UNAUTHORIZED, "id_token이 유효하지 않습니다."),
    PROVIDER_NOT_CONFIGURED(HttpStatus.BAD_REQUEST, "소셜 로그인이 구성되지 않았습니다."),
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 provider 입니다."),
    JWKS_FETCH_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "외부 인증 키 조회에 실패했습니다."),

    // 사용자
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    LINKED_USER_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "연결된 사용자가 존재하지 않습니다."),
    SELF_FOLLOW(HttpStatus.FORBIDDEN, "본인을 팔로우할 수 없습니다."),
    ALREADY_FOLLOWING(HttpStatus.CONFLICT, "이미 팔로우 중인 사용자입니다."),

    // 링크 수집
    SOURCE_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "링크를 찾을 수 없습니다."),
    UNSUPPORTED_PLATFORM(HttpStatus.UNPROCESSABLE_CONTENT, "지원하지 않는 URL 플랫폼입니다."),
    DUPLICATE_LINK(HttpStatus.CONFLICT, "이미 처리 중인 동일 링크입니다."),

    // 분석 잡 / 장소 후보
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "분석 잡을 찾을 수 없습니다."),
    CANDIDATE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소 후보를 찾을 수 없습니다."),
    ALREADY_PROCESSED_CANDIDATE(HttpStatus.CONFLICT, "이미 처리된 후보입니다."),

    // 장소 / 플레이스
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."),
    COLLECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "플레이스를 찾을 수 없습니다."),
    COLLECTION_DEFAULT_PROTECTED(HttpStatus.FORBIDDEN, "기본 플레이스는 삭제할 수 없습니다."),

    // 플랜
    ITINERARY_NOT_FOUND(HttpStatus.NOT_FOUND, "플랜을 찾을 수 없습니다."),
    ITINERARY_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "일정 항목을 찾을 수 없습니다."),

    // 커뮤니티 / 리뷰
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "포스트를 찾을 수 없습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
    ALREADY_LIKED(HttpStatus.CONFLICT, "이미 좋아요한 포스트입니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "리뷰를 찾을 수 없습니다."),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 해당 장소에 리뷰가 존재합니다."),

    // 플랜 마켓 / 크레딧 / 광고
    MARKET_PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "마켓 플랜을 찾을 수 없습니다."),
    OTA_NOT_VERIFIED(HttpStatus.UNPROCESSABLE_CONTENT, "OTA 예약이 완료되지 않은 플랜입니다."),
    ALREADY_LISTED(HttpStatus.CONFLICT, "이미 마켓에 등록된 플랜입니다."),
    INSUFFICIENT_CREDITS(HttpStatus.PAYMENT_REQUIRED, "크레딧이 부족합니다."),
    AD_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "광고 세션을 찾을 수 없습니다."),
    AD_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_CONTENT, "일일 광고 시청 한도를 초과했습니다."),
    INVALID_AD_SESSION(HttpStatus.UNPROCESSABLE_CONTENT, "유효하지 않거나 만료된 광고 세션입니다."),
    AD_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 완료 처리된 광고 세션입니다."),

    // 알림 / 공유
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
    SHARE_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "공유 링크가 유효하지 않습니다."),
    ;

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
