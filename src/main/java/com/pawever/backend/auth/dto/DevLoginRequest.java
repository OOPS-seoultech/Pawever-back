package com.pawever.backend.auth.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DevLoginRequest {
    private String name;
    private String nickname;
}
