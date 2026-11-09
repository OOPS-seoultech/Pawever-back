package com.pawever.backend.admin.service;

import com.pawever.backend.admin.entity.*;
import com.pawever.backend.admin.repository.AdminAccountRepository;
import com.pawever.backend.admin.security.AdminTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "admin.jwt-secret=admin-secret-key-must-be-at-least-256-bits-long-for-hs256-ok")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminInvitationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AdminAccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired AdminTokenProvider tokens;
    @MockitoBean AdminInvitationMailer mailer;

    private AdminAccount account(AdminRole role) {
        var account = AdminAccount.invite(UUID.randomUUID() + "@example.test", "테스트", role,
                UUID.randomUUID().toString(), Instant.now().plusSeconds(3600));
        account.acceptInvite(passwords.encode("12345"));
        account.approve(Set.of(WorkRole.MODELING), Instant.now());
        return accounts.saveAndFlush(account);
    }

    private void login(String email, String password, int status) throws Exception {
        mvc.perform(post("/api/admin/auth/sign-in").contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(status));
    }

    @Test
    void 실패_응답에도_횟수가_커밋되고_성공시_리셋_열번째에는_접근회수() throws Exception {
        var worker = account(AdminRole.PRODUCTION);
        String email = worker.getEmail();
        String oldToken = tokens.createToken(worker.getId(), worker.getRole());
        for (int i = 0; i < 9; i++) login(email, "wrong", 401);
        assertThat(accounts.findById(worker.getId()).orElseThrow().getConsecutiveLoginFailures()).isEqualTo(9);
        login(email, "12345", 200);
        assertThat(accounts.findById(worker.getId()).orElseThrow().getConsecutiveLoginFailures()).isZero();
        for (int i = 0; i < 10; i++) login(email, "wrong", 401);
        assertThat(accounts.findById(worker.getId()).orElseThrow().getStatus()).isEqualTo(AdminAccountStatus.DELETED);
        assertThat(accounts.findByEmail(email)).isEmpty();
        mvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 관리자_삭제후_동일메일_새초대_비밀번호설정_기존토큰무효() throws Exception {
        var owner = account(AdminRole.OWNER);
        var worker = account(AdminRole.PRODUCTION);
        String email = worker.getEmail();
        String ownerAuth = "Bearer " + tokens.createToken(owner.getId(), owner.getRole());
        String oldAuth = "Bearer " + tokens.createToken(worker.getId(), worker.getRole());
        mvc.perform(delete("/api/admin/accounts/" + worker.getId()).header("Authorization", ownerAuth))
                .andExpect(status().isOk());
        assertThat(accounts.findByEmail(email)).isEmpty();
        mvc.perform(get("/api/admin/me").header("Authorization", oldAuth)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/accounts").header("Authorization", ownerAuth).contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"name\":\"다시 초대\",\"role\":\"PRODUCTION\",\"workRoles\":[\"MODELING\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.inviteToken").doesNotExist());
        var token = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mailer).send(org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.eq("다시 초대"), token.capture());
        mvc.perform(post("/api/admin/auth/invitation-info").contentType("application/json")
                .content("{\"inviteToken\":\"" + token.getValue() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value(email));
        String payload = "{\"inviteToken\":\"" + token.getValue() + "\",\"password\":\"12345\"}";
        mvc.perform(post("/api/admin/auth/accept-invite").contentType("application/json").content(payload))
                .andExpect(status().isOk());
        login(email, "12345", 200);
        assertThat(accounts.findByEmail(email).orElseThrow().getId()).isNotEqualTo(worker.getId());
        mvc.perform(post("/api/admin/auth/accept-invite").contentType("application/json").content(payload))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/me").header("Authorization", oldAuth)).andExpect(status().isUnauthorized());
    }

    @Test
    void 메일발송_실패시_계정을_남기지_않는다() throws Exception {
        var owner = account(AdminRole.OWNER);
        String email = UUID.randomUUID() + "@example.test";
        org.mockito.Mockito.doThrow(new com.pawever.backend.global.exception.CustomException(
                com.pawever.backend.global.exception.ErrorCode.ADMIN_MAIL_SEND_FAILED))
                .when(mailer).send(org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        mvc.perform(post("/api/admin/accounts").header("Authorization", "Bearer " + tokens.createToken(owner.getId(), owner.getRole()))
                .contentType("application/json").content("{\"email\":\"" + email + "\",\"name\":\"직원\",\"role\":\"PRODUCTION\",\"workRoles\":[\"MODELING\"]}"))
                .andExpect(status().isBadGateway());
        assertThat(accounts.findByEmail(email)).isEmpty();
    }
}
