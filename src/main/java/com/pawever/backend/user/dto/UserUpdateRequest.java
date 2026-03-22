package com.pawever.backend.user.dto;

import com.pawever.backend.user.entity.ReferralType;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserUpdateRequest {
    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    private String nickname;

    private String phone;

    private String ageRange;

    private ReferralType referralType;

    private String referralMemo;

    /** 알림(푸시) 수신 동의 여부. null이면 변경 없음 */
    private Boolean notificationEnabled;
    /** 마케팅 수신 동의 여부. null이면 변경 없음 */
    private Boolean marketingEnabled;
}
