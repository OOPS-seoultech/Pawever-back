package com.pawever.backend.admin.controller;

import com.pawever.backend.admin.dto.AdminAccountResponse;
import com.pawever.backend.admin.dto.AdminInviteRequest;
import com.pawever.backend.admin.service.AdminAccountService;
import com.pawever.backend.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 담당자 관리.
 *
 * 관리자만 부를 수 있다. 제작팀이 계정을 만들 수 있으면 스스로 권한을 올릴 수 있다.
 */
@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountController {

    private final AdminAccountService accountService;

    @GetMapping
    public ApiResponse<List<AdminAccountResponse>> list() {
        return ApiResponse.ok(
                accountService.list().stream().map(AdminAccountResponse::from).toList()
        );
    }

    /** 초대 값은 지금 한 번만 볼 수 있다. 저장하지 않고 해시만 남긴다. */
    @PostMapping
    public ApiResponse<Map<String, String>> invite(@Valid @RequestBody AdminInviteRequest request) {
        String inviteToken = accountService.invite(request.email(), request.name(), request.role());
        return ApiResponse.ok(Map.of("inviteToken", inviteToken));
    }

    @PostMapping("/{accountId}/reinvite")
    public ApiResponse<Map<String, String>> reinvite(@PathVariable Long accountId) {
        return ApiResponse.ok(Map.of("inviteToken", accountService.reinvite(accountId)));
    }

    /** 지우지 않고 막는다. 상태 변경 이력에 이름이 남아 있다. */
    @DeleteMapping("/{accountId}")
    public ApiResponse<Void> disable(@PathVariable Long accountId) {
        accountService.disable(accountId);
        return ApiResponse.ok();
    }
}
