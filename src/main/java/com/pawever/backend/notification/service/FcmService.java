package com.pawever.backend.notification.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

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
     * @param petProfileImageUrl 댓글 대상 추모관 반려동물 프로필 이미지 URL
     * @param petAnimalTypeKey   댓글 대상 추모관 반려동물 종 key
     */
    public void sendCommentNotification(
            String fcmToken,
            String senderNickname,
            String content,
            Long petId,
            String petProfileImageUrl,
            String petAnimalTypeKey
    ) {
        if (fcmToken == null || fcmToken.isBlank()) {
            return; // 수신 토큰이 없으면 조용히 skip
        }
        try {
            Map<String, String> data = new HashMap<>();
            data.put("title", senderNickname);
            data.put("body", content);
            data.put("petId", String.valueOf(petId));

            if (petProfileImageUrl != null && !petProfileImageUrl.isBlank()) {
                data.put("petProfileImageUrl", petProfileImageUrl);
            }

            if (petAnimalTypeKey != null && !petAnimalTypeKey.isBlank()) {
                data.put("petAnimalTypeKey", petAnimalTypeKey);
            }

            Message message = Message.builder()
                    .setToken(fcmToken)
                    .putAllData(data)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setSound("default")
                                    .setAlert(ApsAlert.builder()
                                            .setTitle(senderNickname)
                                            .setBody(content)
                                            .build())
                                    .build())
                            .build())
                    .build();
            FirebaseMessaging.getInstance().send(message);
        } catch (Exception e) {
            // 알림 실패가 호출측 트랜잭션(댓글 작성 등)을 롤백시키지 않도록 모두 흡수
            log.warn("FCM 전송 실패(무시): {}", e.getMessage());
        }
    }
}
