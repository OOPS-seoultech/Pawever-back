package com.pawever.backend.notification.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FcmService {

    /**
     * 추모관 댓글 알림 전송
     *
     * @param fcmToken       수신자 FCM 토큰
     * @param senderNickname 댓글 작성자 닉네임 (알림 제목)
     * @param content        댓글 내용 (알림 본문)
     * @param petId          댓글 대상 추모관 반려동물 ID
     */
    public void sendCommentNotification(String fcmToken, String senderNickname, String content, Long petId) {
        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder()
                        .setTitle(senderNickname)
                        .setBody(content)
                        .build())
                .putData("petId", String.valueOf(petId))
                .build();
        try {
            FirebaseMessaging.getInstance().send(message);
        } catch (FirebaseMessagingException e) {
            log.error("FCM 전송 실패: {}", e.getMessage());
        }
    }
}
