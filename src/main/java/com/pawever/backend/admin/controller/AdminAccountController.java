package com.pawever.backend.admin.controller;

import com.pawever.backend.admin.dto.AdminAccountResponse;
import com.pawever.backend.admin.dto.AdminApproveRequest;
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

/**
 * 담당자 관리.
 *
 * 관리자만 부를 수 있다. 제작팀이 계정을 만들 수 있으면 스스로 권한을 올릴 수 있다.
 */
@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class AdminAccountController {

    private final AdminAccountService accountService;

    @GetMapping
    public ApiResponse<List<AdminAccountResponse>> list() {
        return ApiResponse.ok(
                accountService.list().stream().map(AdminAccountResponse::from).toList()
        );
    }

    /** 초대 링크는 수신자에게만 메일로 보낸다. */
    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<Void> invite(@Valid @RequestBody AdminInviteRequest request) {
        accountService.invite(request.email(), request.name(), request.role(), request.workRoles());
        return ApiResponse.ok();
    }

    /**
     * 실무 권한을 준다. 전체 관리자만 할 수 있다.
     *
     * 어떤 일을 맡을지는 여기서 정한다. 가입할 때 보낸 값이 권한을 정하면
     * 요청을 고쳐 스스로 권한을 키울 수 있다.
     */
    @PostMapping("/{accountId}/approve")
    public ApiResponse<Void> approve(
            @PathVariable Long accountId,
            @RequestBody(required = false) AdminApproveRequest request
    ) {
        accountService.approve(
                accountId,
                request == null ? java.util.Set.of() : request.workRoles()
        );
        return ApiResponse.ok();
    }

    @PostMapping("/{accountId}/reinvite")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<Void> reinvite(@PathVariable Long accountId) {
        accountService.reinvite(accountId);
        return ApiResponse.ok();
    }

    /** 로그인 신원·권한을 회수하고 과거 작업 참조는 보존한다. */
    @DeleteMapping("/{accountId}")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<Void> remove(@PathVariable Long accountId) {
        accountService.remove(accountId);
        return ApiResponse.ok();
    }
}
