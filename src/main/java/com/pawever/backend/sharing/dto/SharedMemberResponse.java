package com.pawever.backend.sharing.dto;

import com.pawever.backend.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SharedMemberResponse {
    private Long userId;
    private String name;
    private String nickname;
    private String profileImageUrl;
    private Boolean isOwner;

    public static SharedMemberResponse of(User user, Boolean isOwner) {
        return SharedMemberResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .isOwner(isOwner)
                .build();
    }
}
