package com.pawever.backend.admin.service;

import com.pawever.backend.admin.entity.AdminAccount;
import com.pawever.backend.admin.entity.AdminAccountStatus;
import com.pawever.backend.admin.entity.AdminRole;
import com.pawever.backend.admin.entity.WorkRole;
import com.pawever.backend.admin.config.AdminProperties;
import com.pawever.backend.admin.repository.AdminAccountRepository;
import com.pawever.backend.admin.security.AdminTokenProvider;
import com.pawever.backend.admin.security.AdminPrincipal;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import com.pawever.backend.global.security.HmacHasher;
import jakarta.annotation.PostConstruct;
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

/** 이메일 초대, 비밀번호 설정, 로그인 및 계정 접근 회수. */
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int INVITE_VALID_DAYS = 7;
    private static final int MIN_PASSWORD_LENGTH = 5;

    private final AdminAccountRepository accountRepository;
    private final AdminTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final HmacHasher hmacHasher;
    private final AdminProperties properties;
    private final Clock clock;
    private final AdminInvitationMailer invitationMailer;

    /**
     * 없는 계정으로 로그인을 시도했을 때 대신 맞춰 보는 값.
     *
     * 아무도 모르는 값이라 어떤 비밀번호도 맞지 않는다. 맞추려고 두는 것이
     * 아니라 같은 시간을 쓰려고 둔다.
     *
     * 기동할 때 미리 만든다. 처음 요청에서 만들면 그 요청만 두 배로 걸린다.
     * 다만 비어 있으면 대조가 곧바로 false 를 돌려주고 시간이 다시 갈리므로,
     * 읽는 쪽에서 한 번 더 확인한다.
     */
    private volatile String absentAccountHash;

    @PostConstruct
    void prepareAbsentAccountHash() {
        absentAccountHash();
    }

    private String absentAccountHash() {
        String prepared = absentAccountHash;
        if (prepared == null) {
            byte[] noise = new byte[32];
            RANDOM.nextBytes(noise);
            prepared = passwordEncoder.encode(Base64.getEncoder().encodeToString(noise));
            absentAccountHash = prepared;
        }
        return prepared;
    }

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
        if (accountRepository.existsByRole(AdminRole.ADMIN) || accountRepository.existsByRole(AdminRole.OWNER)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        return createInvite(email, name, AdminRole.ADMIN);
    }

    /** 대표가 작업 역할을 정하고 일회용 가입 링크를 메일로 보낸다. */
    @Transactional
    public void invite(String email, String name, AdminRole role, java.util.Set<WorkRole> workRoles) {
        accountRepository.lockAccounts();
        if (role != null && role != AdminRole.PRODUCTION) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        String normalized = normalizeEmail(email);
        if (accountRepository.findByEmail(normalized).isPresent()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        if (workRoles == null || workRoles.stream().anyMatch(java.util.Objects::isNull)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        String token = randomToken();
        AdminAccount account = AdminAccount.invite(normalized, name, AdminRole.PRODUCTION,
                hmacHasher.hash(token), inviteExpiry());
        account.setWorkRoles(workRoles);
        account.touchPermissions(clock.instant());
        accountRepository.saveAndFlush(account);
        invitationMailer.send(normalized, name, token);
    }

    /** 초대를 다시 보낸다. 앞서 보낸 링크는 그 순간 쓸 수 없게 된다. */
    @Transactional
    public void reinvite(Long accountId) {
        accountRepository.lockAccounts();
        AdminAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_ACCOUNT_NOT_FOUND));
        requireEditable(account);
        if (account.getStatus() != AdminAccountStatus.INVITED) {
            throw new CustomException(ErrorCode.ADMIN_ACCOUNT_ALREADY_HAS_PASSWORD);
        }
        String inviteToken = randomToken();
        account.reinvite(hmacHasher.hash(inviteToken), inviteExpiry());
        invitationMailer.send(account.getEmail(), account.getName(), inviteToken);
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, String> invitationInfo(String token) {
        AdminAccount account = usableInvitation(token);
        return java.util.Map.of("email", account.getEmail(), "name", account.getName());
    }

    private AdminAccount usableInvitation(String token) {
        if (token == null || token.isBlank()) throw new CustomException(ErrorCode.INVALID_TOKEN);
        AdminAccount account = accountRepository.findByInviteTokenHash(hmacHasher.hash(token))
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));
        if (!account.isInviteUsable(clock.instant())) throw new CustomException(ErrorCode.EXPIRED_TOKEN);
        return account;
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
        accountRepository.lockAccounts();
        AdminAccount account = usableInvitation(inviteToken);
        account.acceptInvite(passwordEncoder.encode(password));
    }

    /**
     * 대표가 실무 권한을 준다.
     *
     * 누가 언제 승인했는지 남긴다. 승인 없이 쓸 수 있는 계정이 생기면
     * 초대 링크가 한 번 새는 것으로 고객 정보가 통째로 열린다.
     */
    @Transactional
    public void approve(Long accountId, java.util.Set<WorkRole> workRoles) {
        AdminPrincipal actor = AdminPrincipal.current();
        if (actor == null || actor.role() != AdminRole.OWNER) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        accountRepository.lockAccounts();
        AdminAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_ACCOUNT_NOT_FOUND));
        if (!account.isAwaitingApproval()) {
            throw new CustomException(ErrorCode.ADMIN_ACCOUNT_NOT_AWAITING_APPROVAL);
        }
        // 승인은 실무 권한만 준다. 전체 관리자는 한 사람뿐이다.
        if (account.getRole() != AdminRole.PRODUCTION) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        account.approve(
                workRoles == null ? java.util.Set.of() : workRoles,
                clock.instant()
        );
    }

    /**
     * 로그인한다.
     *
     * 계정이 없는 것과 비밀번호가 틀린 것을 같은 응답으로 돌려준다. 나눠서
     * 알려주면 어떤 주소가 등록돼 있는지 확인하는 통로가 된다.
     */
    @Transactional(noRollbackFor = CustomException.class)
    public String signIn(String email, String password) {
        if (!tokenProvider.isEnabled()) {
            throw new CustomException(ErrorCode.ADMIN_SIGN_IN_DISABLED);
        }
        AdminAccount account = accountRepository.lockByEmail(normalizeEmail(email))
                .orElse(null);
        // 승인 대기도 비밀번호는 있다. 대조까지 마친 뒤에 무엇이 막았는지
        // 말해 준다 — 먼저 걸러 내면 비밀번호를 모르는 사람도 그 주소가
        // 등록돼 있다는 것을 알게 된다.
        boolean usable = account != null && account.hasPassword();

        // 계정이 없어도 대조를 거친다. 없다고 곧바로 돌려보내면 응답이 눈에 띄게
        // 빨라서, 오류 문구가 같아도 어떤 주소가 등록돼 있는지 시간으로 알 수 있다.
        // 비밀번호를 아직 정하지 않은 계정도 같다.
        String hash = usable ? account.getPasswordHash() : absentAccountHash();
        boolean matched = passwordEncoder.matches(password, hash);

        if (!usable || !matched) {
            if (usable && !matched && account.getRole() != AdminRole.OWNER
                    && account.recordLoginFailure() >= 10) {
                account.removeAccess(clock.instant());
            }
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        account.resetLoginFailures();
        if (account.isAwaitingApproval()) {
            throw new CustomException(ErrorCode.ADMIN_ACCOUNT_PENDING_APPROVAL);
        }
        if (!account.canSignIn()) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        account.recordLogin(clock.instant());
        return tokenProvider.createToken(account.getId(), account.getRole());
    }

    @Transactional(readOnly = true)
    public List<AdminAccount> list() {
        return accountRepository.findAllByOrderByCreatedAtAsc().stream()
                .filter(a -> a.getStatus() != AdminAccountStatus.DELETED).toList();
    }

    @Transactional
    public void remove(Long accountId) {
        accountRepository.lockAccounts();
        AdminAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new CustomException(ErrorCode.ADMIN_ACCOUNT_NOT_FOUND));
        requireEditable(account);
        account.removeAccess(clock.instant());
    }

    private void requireEditable(AdminAccount target) {
        AdminPrincipal actor = AdminPrincipal.current();
        if (target.getStatus() == AdminAccountStatus.DELETED)
            throw new CustomException(ErrorCode.ADMIN_ACCOUNT_NOT_FOUND);
        if (actor != null && actor.accountId().equals(target.getId())) throw new CustomException(ErrorCode.FORBIDDEN);
        if (target.getRole() == AdminRole.OWNER) throw new CustomException(ErrorCode.FORBIDDEN);
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
