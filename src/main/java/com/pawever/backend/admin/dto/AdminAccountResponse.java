package com.pawever.backend.admin.dto;

import com.pawever.backend.admin.entity.AdminAccount;
import com.pawever.backend.admin.entity.AdminAccountStatus;
import com.pawever.backend.admin.entity.AdminRole;

import java.time.Instant;

/** 계정 목록에 쓰는 값. 비밀번호 해시와 초대 값은 담지 않는다. */
public record AdminAccountResponse(
        Long id,
        String email,
        String name,
        AdminRole role,
        AdminAccountStatus status,
        Instant lastLoginAt
) {
    public static AdminAccountResponse from(AdminAccount account) {
        return new AdminAccountResponse(
                account.getId(),
                account.getEmail(),
                account.getName(),
                account.getRole(),
                account.getStatus(),
                account.getLastLoginAt()
        );
    }
}
