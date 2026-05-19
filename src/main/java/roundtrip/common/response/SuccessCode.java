package roundtrip.common.response;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum SuccessCode {

    // 공통 폴백
    RESOURCE_FETCHED(HttpStatus.OK, "리소스 조회에 성공했습니다."),
    RESOURCE_CREATED(HttpStatus.CREATED, "리소스 생성에 성공했습니다."),
    RESOURCE_UPDATED(HttpStatus.OK, "리소스 수정에 성공했습니다."),
    RESOURCE_DELETED(HttpStatus.NO_CONTENT, "리소스 삭제에 성공했습니다."),

    // 인증
    AUTH_LOGIN_SUCCESS(HttpStatus.CREATED, "로그인에 성공했습니다."),
    AUTH_TOKEN_REFRESHED(HttpStatus.OK, "액세스 토큰이 갱신되었습니다."),
    AUTH_LOGOUT_SUCCESS(HttpStatus.NO_CONTENT, "로그아웃에 성공했습니다."),

    // 사용자
    USER_PROFILE_FETCHED(HttpStatus.OK, "내 프로필 조회에 성공했습니다."),
    USER_PROFILE_UPDATED(HttpStatus.OK, "프로필 수정에 성공했습니다."),
    USER_ACCOUNT_DELETED(HttpStatus.NO_CONTENT, "계정 삭제에 성공했습니다."),
    USER_PUBLIC_PROFILE_FETCHED(HttpStatus.OK, "사용자 프로필 조회에 성공했습니다."),
    USER_FOLLOW_CREATED(HttpStatus.CREATED, "팔로우에 성공했습니다."),
    USER_UNFOLLOW_SUCCESS(HttpStatus.NO_CONTENT, "언팔로우에 성공했습니다."),

    // 링크 수집
    SOURCE_LINK_SUBMITTED(HttpStatus.CREATED, "링크 제출 및 분석 잡 생성에 성공했습니다."),
    SOURCE_LINK_LIST_FETCHED(HttpStatus.OK, "링크 목록 조회에 성공했습니다."),

    // 분석 잡
    JOB_STATUS_FETCHED(HttpStatus.OK, "분석 잡 상태 조회에 성공했습니다."),

    // 장소 후보
    CANDIDATE_LIST_FETCHED(HttpStatus.OK, "장소 후보 목록 조회에 성공했습니다."),
    CANDIDATE_UPDATED(HttpStatus.OK, "후보 처리에 성공했습니다."),
    CANDIDATE_BATCH_PROCESSED(HttpStatus.OK, "후보 일괄 처리에 성공했습니다."),

    // 장소
    PLACE_FETCHED(HttpStatus.OK, "장소 상세 조회에 성공했습니다."),
    PLACE_SEARCH_FETCHED(HttpStatus.OK, "장소 검색에 성공했습니다."),
    PLACE_SIMILAR_FETCHED(HttpStatus.OK, "유사 장소 추천 조회에 성공했습니다."),
    PLACE_DISCOVER_FETCHED(HttpStatus.OK, "둘러보기 추천 조회에 성공했습니다."),
    PLACE_SOURCE_LINK_LIST_FETCHED(HttpStatus.OK, "장소 출처 영상 목록 조회에 성공했습니다."),

    // 장소 리뷰
    REVIEW_LIST_FETCHED(HttpStatus.OK, "장소 리뷰 목록 조회에 성공했습니다."),
    REVIEW_CREATED(HttpStatus.CREATED, "리뷰 작성에 성공했습니다."),
    REVIEW_DELETED(HttpStatus.NO_CONTENT, "리뷰 삭제에 성공했습니다."),

    // 플레이스 (컬렉션)
    COLLECTION_LIST_FETCHED(HttpStatus.OK, "플레이스 목록 조회에 성공했습니다."),
    COLLECTION_CREATED(HttpStatus.CREATED, "플레이스 생성에 성공했습니다."),
    COLLECTION_UPDATED(HttpStatus.OK, "플레이스 수정에 성공했습니다."),
    COLLECTION_DELETED(HttpStatus.NO_CONTENT, "플레이스 삭제에 성공했습니다."),
    COLLECTION_PLACE_LIST_FETCHED(HttpStatus.OK, "플레이스 내 장소 목록 조회에 성공했습니다."),
    COLLECTION_PLACE_ADDED(HttpStatus.CREATED, "플레이스에 장소 추가에 성공했습니다."),
    COLLECTION_PLACE_REMOVED(HttpStatus.NO_CONTENT, "플레이스에서 장소 제거에 성공했습니다."),
    COLLECTION_SHARE_FETCHED(HttpStatus.OK, "플레이스 공유 링크 조회에 성공했습니다."),

    // 플랜 (일정)
    ITINERARY_LIST_FETCHED(HttpStatus.OK, "플랜 목록 조회에 성공했습니다."),
    ITINERARY_CREATED(HttpStatus.CREATED, "플랜 생성에 성공했습니다."),
    ITINERARY_FETCHED(HttpStatus.OK, "플랜 상세 조회에 성공했습니다."),
    ITINERARY_UPDATED(HttpStatus.OK, "플랜 수정에 성공했습니다."),
    ITINERARY_DELETED(HttpStatus.NO_CONTENT, "플랜 삭제에 성공했습니다."),
    ITINERARY_ITEM_ADDED(HttpStatus.CREATED, "플랜에 장소 추가에 성공했습니다."),
    ITINERARY_ITEM_UPDATED(HttpStatus.OK, "장소 일정 수정에 성공했습니다."),
    ITINERARY_ITEM_REMOVED(HttpStatus.NO_CONTENT, "플랜에서 장소 제거에 성공했습니다."),
    ITINERARY_ITEM_REORDERED(HttpStatus.OK, "일정 순서 변경에 성공했습니다."),
    ITINERARY_SHARE_FETCHED(HttpStatus.OK, "플랜 공유 링크 조회에 성공했습니다."),
    ITINERARY_OTA_LINK_GENERATED(HttpStatus.OK, "OTA 예약 링크 생성에 성공했습니다."),

    // Planning Agent
    AGENT_MESSAGE_PROCESSED(HttpStatus.OK, "Agent 메시지 처리에 성공했습니다."),

    // 커뮤니티
    POST_LIST_FETCHED(HttpStatus.OK, "커뮤니티 피드 조회에 성공했습니다."),
    POST_CREATED(HttpStatus.CREATED, "포스트 작성에 성공했습니다."),
    POST_FETCHED(HttpStatus.OK, "포스트 상세 조회에 성공했습니다."),
    POST_DELETED(HttpStatus.NO_CONTENT, "포스트 삭제에 성공했습니다."),
    POST_LIKE_CREATED(HttpStatus.CREATED, "좋아요에 성공했습니다."),
    POST_LIKE_REMOVED(HttpStatus.OK, "좋아요 취소에 성공했습니다."),
    COMMENT_LIST_FETCHED(HttpStatus.OK, "댓글 목록 조회에 성공했습니다."),
    COMMENT_CREATED(HttpStatus.CREATED, "댓글 작성에 성공했습니다."),
    COMMENT_DELETED(HttpStatus.NO_CONTENT, "댓글 삭제에 성공했습니다."),

    // 플랜 마켓
    MARKET_PLAN_LIST_FETCHED(HttpStatus.OK, "플랜 마켓 목록 조회에 성공했습니다."),
    MARKET_PLAN_LISTED(HttpStatus.CREATED, "플랜 마켓 등록에 성공했습니다."),
    MARKET_PLAN_PREVIEW_FETCHED(HttpStatus.OK, "플랜 마켓 미리보기 조회에 성공했습니다."),
    MARKET_PLAN_FETCHED(HttpStatus.OK, "플랜 마켓 상세 조회에 성공했습니다."),
    MARKET_PLAN_UNLISTED(HttpStatus.NO_CONTENT, "플랜 마켓 등록 취소에 성공했습니다."),

    // 크레딧 / 광고
    CREDIT_BALANCE_FETCHED(HttpStatus.OK, "크레딧 잔액 조회에 성공했습니다."),
    CREDIT_HISTORY_FETCHED(HttpStatus.OK, "크레딧 내역 조회에 성공했습니다."),
    AD_SESSION_STARTED(HttpStatus.CREATED, "광고 시청 세션 시작에 성공했습니다."),
    AD_VIEW_COMPLETED(HttpStatus.OK, "광고 시청 완료 처리에 성공했습니다."),

    // 알림
    NOTIFICATION_LIST_FETCHED(HttpStatus.OK, "알림 목록 조회에 성공했습니다."),
    NOTIFICATION_READ(HttpStatus.OK, "알림 읽음 처리에 성공했습니다."),

    // 공개 공유
    PUBLIC_ITINERARY_FETCHED(HttpStatus.OK, "공유 플랜 조회에 성공했습니다."),
    PUBLIC_COLLECTION_FETCHED(HttpStatus.OK, "공유 플레이스 조회에 성공했습니다."),
    ;

    private final HttpStatus status;
    private final String message;

    SuccessCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
