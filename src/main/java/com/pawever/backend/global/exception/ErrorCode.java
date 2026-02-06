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

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    USER_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "이미 탈퇴한 사용자입니다."),

    // Pet
    PET_NOT_FOUND(HttpStatus.NOT_FOUND, "반려동물을 찾을 수 없습니다."),
    PET_NOT_OWNED(HttpStatus.FORBIDDEN, "해당 반려동물에 대한 권한이 없습니다."),
    BREED_NOT_FOUND(HttpStatus.NOT_FOUND, "품종을 찾을 수 없습니다."),
    ANIMAL_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "동물 종류를 찾을 수 없습니다."),

    // Mission
    MISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "미션을 찾을 수 없습니다."),

    // Memorial
    MEMORIAL_NOT_FOUND(HttpStatus.NOT_FOUND, "추모 정보를 찾을 수 없습니다."),
    MEMORIAL_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 추모가 생성되었습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),

    // Funeral
    FUNERAL_COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "장례업체를 찾을 수 없습니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "후기를 찾을 수 없습니다."),

    // Sharing
    INVALID_INVITE_CODE(HttpStatus.BAD_REQUEST, "유효하지 않은 초대코드입니다."),
    ALREADY_SHARED(HttpStatus.BAD_REQUEST, "이미 공유된 반려동물입니다."),
    NOT_OWNER(HttpStatus.FORBIDDEN, "소유자만 수행할 수 있습니다."),
    CANNOT_REMOVE_OWNER(HttpStatus.BAD_REQUEST, "소유자는 공유 해제할 수 없습니다."),

    // Checklist
    CHECKLIST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "체크리스트 항목을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
