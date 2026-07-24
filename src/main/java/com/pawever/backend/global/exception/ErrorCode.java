package com.pawever.backend.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력값입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),

    // Auth
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    KAKAO_API_ERROR(HttpStatus.BAD_GATEWAY, "카카오 API 호출에 실패했습니다."),
    NAVER_API_ERROR(HttpStatus.BAD_GATEWAY, "네이버 API 호출에 실패했습니다."),
    APPLE_API_ERROR(HttpStatus.BAD_GATEWAY, "Apple API 호출에 실패했습니다."),
    APPLE_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 Apple 토큰입니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    USER_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "이미 탈퇴한 사용자입니다."),
    DUPLICATE_PHONE(HttpStatus.CONFLICT, "이미 가입된 전화번호입니다."),
    DUPLICATE_PHONE_KAKAO(HttpStatus.CONFLICT, "이미 카카오로 가입된 계정이 있어요.\n카카오로 로그인해주세요!"),
    DUPLICATE_PHONE_NAVER(HttpStatus.CONFLICT, "이미 네이버로 가입된 계정이 있어요.\n네이버로 로그인해주세요!"),
    DUPLICATE_PHONE_APPLE(HttpStatus.CONFLICT, "이미 Apple로 가입된 계정이 있어요.\nApple로 로그인해주세요!"),

    // Pet
    PET_NOT_FOUND(HttpStatus.NOT_FOUND, "반려동물을 찾을 수 없습니다."),
    /** 선택된 반려동물이 이미 삭제된 경우 (owner 탈퇴 등) — 홈 접근 시 "이용 중이던 반려동물 프로필이 삭제되었습니다" 안내용 */
    SELECTED_PET_DELETED(HttpStatus.GONE, "이용 중이던 반려동물 프로필이 삭제되었습니다."),
    PET_NOT_OWNED(HttpStatus.FORBIDDEN, "해당 반려동물에 대한 권한이 없습니다."),
    BREED_NOT_FOUND(HttpStatus.NOT_FOUND, "품종을 찾을 수 없습니다."),
    ANIMAL_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "동물 종류를 찾을 수 없습니다."),
    INVALID_DEATH_DATE(HttpStatus.BAD_REQUEST, "이별 상태에 맞는 이별 일자를 입력해 주세요."),
    OWNER_PET_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "소유자로 등록할 수 있는 반려동물은 최대 1마리입니다."),
    GUEST_PET_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "게스트로 참여할 수 있는 반려동물은 최대 10마리입니다."),

    // Mission
    MISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "미션을 찾을 수 없습니다."),

    // Memorial
    MEMORIAL_NOT_FOUND(HttpStatus.NOT_FOUND, "추모 정보를 찾을 수 없습니다."),
    MEMORIAL_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 추모가 생성되었습니다."),
    EMERGENCY_MODE_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "긴급 대처 모드가 활성화되어 있지 않습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
    REPORT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "신고 사유를 선택하거나 직접 입력해 주세요."),

    // Funeral
    FUNERAL_COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "장례업체를 찾을 수 없습니다."),
    SAVED_COMPANY_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "저장 가능한 장례업체는 최대 5개입니다."),
    BLOCKED_COMPANY_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "피하기 가능한 장례업체는 최대 15개입니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "후기를 찾을 수 없습니다."),

    // Sharing
    INVALID_INVITE_CODE(HttpStatus.BAD_REQUEST, "유효하지 않은 초대코드입니다."),
    EXPIRED_INVITE_CODE(HttpStatus.GONE, "만료된 초대코드입니다."),
    ALREADY_SHARED(HttpStatus.BAD_REQUEST, "이미 공유된 반려동물입니다."),
    NOT_OWNER(HttpStatus.FORBIDDEN, "소유자만 수행할 수 있습니다."),
    CANNOT_REMOVE_OWNER(HttpStatus.BAD_REQUEST, "소유자는 공유 해제할 수 없습니다."),

    // File
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다. (jpg, png, webp, heic, heif만 허용)"),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "파일 용량이 너무 큽니다. (최대 10MB)"),

    // Goods survey
    SURVEY_CAMPAIGN_NOT_FOUND(HttpStatus.NOT_FOUND, "설문 캠페인을 찾을 수 없습니다."),
    SURVEY_CAMPAIGN_CLOSED(HttpStatus.GONE, "굿즈 신청 기간이 종료되었습니다."),
    SURVEY_CAMPAIGN_FULL(HttpStatus.CONFLICT, "무료 굿즈 선착순 모집이 마감되었습니다."),
    SURVEY_RESPONSE_NOT_FOUND(HttpStatus.NOT_FOUND, "설문 응답을 찾을 수 없습니다."),
    SURVEY_EDIT_TOKEN_INVALID(HttpStatus.FORBIDDEN, "설문 편집 권한이 없습니다."),
    SURVEY_INVALID_ANSWERS(HttpStatus.BAD_REQUEST, "설문 응답 형식 또는 분기가 올바르지 않습니다."),
    SURVEY_INVALID_STATE(HttpStatus.CONFLICT, "현재 설문 상태에서는 요청을 처리할 수 없습니다."),
    SURVEY_RESERVATION_EXPIRED(HttpStatus.GONE, "선착순 예약 시간이 만료되었습니다."),
    SURVEY_DUPLICATE_PHONE(HttpStatus.CONFLICT, "이미 이 캠페인에 신청한 연락처입니다."),
    SURVEY_IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "같은 제출 키로 다른 요청을 처리할 수 없습니다."),
    SURVEY_PHOTO_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "사진은 최대 5장까지 업로드할 수 있습니다."),
    SURVEY_PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND, "업로드할 사진 정보를 찾을 수 없습니다."),
    SURVEY_PHOTO_NOT_READY(HttpStatus.BAD_REQUEST, "사진 업로드가 완료되지 않았습니다."),
    SURVEY_STORAGE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "사진 저장소가 아직 설정되지 않았습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
