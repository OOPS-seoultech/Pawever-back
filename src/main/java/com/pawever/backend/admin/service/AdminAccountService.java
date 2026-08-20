package com.pawever.backend.admin.service;

import com.pawever.backend.admin.entity.AdminAccount;
import com.pawever.backend.admin.entity.AdminRole;
import com.pawever.backend.admin.config.AdminProperties;
import com.pawever.backend.admin.repository.AdminAccountRepository;
import com.pawever.backend.admin.security.AdminTokenProvider;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.global.security.HmacHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * 관리자 계정을 만들고 로그인시킨다.
 *
 * 비밀번호를 만들어 주지 않는다. 초대 값을 한 번 보여주고, 본인이 그것으로
 * 비밀번호를 정한다. 만들어 주면 누군가는 그 값을 메신저에 적어 전달한다.
 */
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int INVITE_VALID_DAYS = 7;
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final AdminAccountRepository accountRepository;
    private final AdminTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final HmacHasher hmacHasher;
    private final AdminProperties properties;
    private final Clock clock;

    /**
     * 첫 관리자를 세운다.
     *
     * 초대할 사람이 아직 없어 생기는 닭과 달걀을 푸는 통로다. 관리자가 하나라도
     * 있으면 더는 열리지 않는다. 남겨 두면 이 통로로 관리자를 계속 만들 수 있다.
     */
    @Transactional
    public String bootstrapFirstAdmin(
            String bootstrapToken,
            String email,
            String name
    ) {
        requireBootstrapToken(bootstrapToken);
        if (accountRepository.existsByRole(AdminRole.ADMIN)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        return createInvite(email, name, AdminRole.ADMIN);
    }

    /** 계정을 만들고 초대 값을 돌려준다. 이 값은 지금 한 번만 볼 수 있다. */
    @Transactional
    public String invite(String email, String name, AdminRole role) {
        String normalized = normalizeEmail(email);
        if (accountRepository.findByEmail(normalized).isPresent()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return createInvite(normalized, name, role);
    }

    /** 초대를 다시 보낸다. 앞서 보낸 링크는 그 순간 쓸 수 없게 된다. */
    @Transactional
    public String reinvite(Long accountId) {
        AdminAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_ACCOUNT_NOT_FOUND));
        String inviteToken = randomToken();
        account.reinvite(hmacHasher.hash(inviteToken), inviteExpiry());
        return inviteToken;
    }

    /**
     * 초대를 받아 비밀번호를 정한다.
     *
     * 초대 값이 맞는지, 아직 쓸 수 있는지 둘 다 본다. 쓰고 나면 그 자리에서
     * 버려서 같은 링크로 두 번 계정을 세울 수 없게 한다.
     */
    @Transactional
    public void acceptInvite(String inviteToken, String password) {
        requirePasswordStrength(password);
        AdminAccount account = accountRepository
                .findByInviteTokenHash(hmacHasher.hash(inviteToken))
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));
        if (!account.isInviteUsable(clock.instant())) {
            throw new CustomException(ErrorCode.EXPIRED_TOKEN);
        }
        account.activate(passwordEncoder.encode(password));
    }

    /**
     * 로그인한다.
     *
     * 계정이 없는 것과 비밀번호가 틀린 것을 같은 응답으로 돌려준다. 나눠서
     * 알려주면 어떤 주소가 등록돼 있는지 확인하는 통로가 된다.
     */
    @Transactional
    public String signIn(String email, String password) {
        if (!tokenProvider.isEnabled()) {
            throw new CustomException(ErrorCode.ADMIN_SIGN_IN_DISABLED);
        }
        AdminAccount account = accountRepository.findByEmail(normalizeEmail(email))
                .orElse(null);
        if (account == null
                || !account.canSignIn()
                || !passwordEncoder.matches(password, account.getPasswordHash())) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        account.recordLogin(clock.instant());
        return tokenProvider.createToken(account.getId(), account.getRole());
    }

    @Transactional(readOnly = true)
    public List<AdminAccount> list() {
        return accountRepository.findAllByOrderByCreatedAtAsc();
    }

    @Transactional
    public void disable(Long accountId) {
        AdminAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_ACCOUNT_NOT_FOUND));
        account.disable();
    }

    private String createInvite(String email, String name, AdminRole role) {
        String inviteToken = randomToken();
        accountRepository.save(AdminAccount.invite(
                normalizeEmail(email),
                name,
                role,
                hmacHasher.hash(inviteToken),
                inviteExpiry()
        ));
        return inviteToken;
    }

    private Instant inviteExpiry() {
        return clock.instant().plus(INVITE_VALID_DAYS, ChronoUnit.DAYS);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 이 계정 하나가 고객 주소와 연락처 전부를 연다.
     *
     * 길이만 본다. 규칙을 잘게 걸면 사람들은 규칙을 겨우 넘기는 짧은 값을 쓴다.
     */
    private void requirePasswordStrength(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new CustomException(ErrorCode.ADMIN_PASSWORD_TOO_SHORT);
        }
    }

    private void requireBootstrapToken(String provided) {
        String bootstrapToken = properties.getBootstrapToken();
        if (bootstrapToken == null || bootstrapToken.isBlank() || provided == null) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        boolean matches = MessageDigest.isEqual(
                bootstrapToken.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8)
        );
        if (!matches) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }
}
