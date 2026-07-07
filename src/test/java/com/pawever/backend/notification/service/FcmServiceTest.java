package com.pawever.backend.notification.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 알림 전송 실패가 호출측 트랜잭션(댓글 작성 등)을 깨뜨리지 않아야 한다.
 */
class FcmServiceTest {

    private final FcmService fcmService = new FcmService();

    @Test
    void sendCommentNotification_whenTokenBlankOrNull_returnsSilently() {
        assertDoesNotThrow(() -> fcmService.sendCommentNotification("", "nick", "content", 1L, null, null));
        assertDoesNotThrow(() -> fcmService.sendCommentNotification(null, "nick", "content", 1L, null, null));
    }

    @Test
    void sendCommentNotification_whenFirebaseNotInitialized_swallowsExceptionAndDoesNotThrow() {
        // Firebase 미초기화 테스트 환경 → getInstance()가 예외를 던지지만 흡수되어야 한다.
        assertDoesNotThrow(() ->
                fcmService.sendCommentNotification("some-token", "nick", "content", 1L, null, null));
    }
}
