package com.pawever.backend.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FcmTokenResponse {
    private String fcmToken;
}
