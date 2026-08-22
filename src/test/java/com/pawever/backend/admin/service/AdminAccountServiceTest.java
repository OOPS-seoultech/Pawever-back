package com.pawever.backend.admin.service;

import com.pawever.backend.admin.config.AdminProperties;
import com.pawever.backend.admin.entity.AdminAccount;
import com.pawever.backend.admin.entity.AdminAccountStatus;
import com.pawever.backend.admin.entity.AdminRole;
import com.pawever.backend.admin.repository.AdminAccountRepository;
import com.pawever.backend.admin.security.AdminTokenProvider;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.security.HmacHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 계정을 만들고 들여보내는 길에 구멍이 없는지 본다.
 *
 * 이 계정 하나가 고객 주소와 연락처 전부를 연다.
 */
@ExtendWith(MockitoExtension.class)
class AdminAccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");
    private static final String ADMIN_SECRET =
            "admin-secret-key-must-be-at-least-256-bits-long-for-hs256-ok";

    @Mock private AdminAccountRepository accountRepository;

    private AdminAccountService service;
    private AdminProperties properties;
    private HmacHasher hmacHasher;
    private BCryptPasswordEncoder passwordEncoder;
    private final java.util.concurrent.atomic.AtomicInteger matchCalls =
            new java.util.concurrent.atomic.AtomicInteger();

    @BeforeEach
    void setUp() {
        properties = new AdminProperties();
        properties.setJwtSecret(ADMIN_SECRET);
        properties.setJwtExpirationMillis(8 * 60 * 60 * 1000L);
        properties.setBootstrapToken("bootstrap-secret");

        hmacHasher = new HmacHasher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        // 대조가 실제로 불렸는지 세어 둔다. 시간을 재는 것은 흔들려서 못 쓰고,
        // 스파이를 씌우면 when(...) 안에서 인코더를 부르는 다른 시험이 깨진다.
        matchCalls.set(0);
        passwordEncoder = new BCryptPasswordEncoder() {
            // matches 는 final 이다. 실제 대조까지 내려온 것만 센다 —
            // 해시가 비어 있으면 여기까지 오지 않고 곧바로 false 다.
            @Override
            protected boolean matchesNonNull(String rawPassword, String encodedPassword) {
                matchCalls.incrementAndGet();
                return super.matchesNonNull(rawPassword, encodedPassword);
            }
        };
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        service = new AdminAccountService(
                accountRepository,
                new AdminTokenProvider(properties, clock),
                passwordEncoder,
                hmacHasher,
                properties,
                clock
        );
    }

    @Test
    void 초대_값은_원본이_아니라_해시로만_남는다() {
        // 원본을 저장하면 데이터베이스를 들여다본 사람이 남의 초대 링크를
        // 그대로 만들어 계정을 가로챌 수 있다.
        when(accountRepository.findByEmail("a@example.com")).thenReturn(Optional.empty());

        String inviteToken = service.invite("A@Example.com ", "나혜", AdminRole.PRODUCTION);

        ArgumentCaptor<AdminAccount> saved = ArgumentCaptor.forClass(AdminAccount.class);
        verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getInviteTokenHash()).isEqualTo(hmacHasher.hash(inviteToken));
        assertThat(saved.getValue().getInviteTokenHash()).isNotEqualTo(inviteToken);
        // 주소는 대소문자와 공백을 정리해 저장한다.
        assertThat(saved.getValue().getEmail()).isEqualTo("a@example.com");
        assertThat(saved.getValue().getPasswordHash()).isNull();
    }

    @Test
    void 관리자가_이미_있으면_첫_관리자_통로가_닫힌다() {
        // 남겨 두면 이 통로로 관리자를 계속 만들 수 있다.
        when(accountRepository.existsByRole(AdminRole.ADMIN)).thenReturn(true);

        assertThatThrownBy(() ->
                service.bootstrapFirstAdmin("bootstrap-secret", "a@example.com", "종무"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 부트스트랩_토큰이_설정되지_않으면_통로가_닫힌다() {
        properties.setBootstrapToken("");

        assertThatThrownBy(() ->
                service.bootstrapFirstAdmin(null, "a@example.com", "종무"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 초대를_받아_비밀번호를_정하면_초대_값은_버려진다() {
        String inviteToken = "invite-token";
        AdminAccount account = invitedAccount(inviteToken, NOW.plusSeconds(3600));
        when(accountRepository.findByInviteTokenHash(hmacHasher.hash(inviteToken)))
                .thenReturn(Optional.of(account));

        service.acceptInvite(inviteToken, "충분히-길고-안전한-비밀번호");

        assertThat(account.getStatus()).isEqualTo(AdminAccountStatus.ACTIVE);
        assertThat(account.getInviteTokenHash()).isNull();
        // 같은 링크로 두 번 계정을 세울 수 없다.
        assertThat(account.isInviteUsable(NOW)).isFalse();
        assertThat(passwordEncoder.matches("충분히-길고-안전한-비밀번호", account.getPasswordHash())).isTrue();
    }

    @Test
    void 기한이_지난_초대는_받지_않는다() {
        String inviteToken = "invite-token";
        AdminAccount account = invitedAccount(inviteToken, NOW.minusSeconds(1));
        when(accountRepository.findByInviteTokenHash(hmacHasher.hash(inviteToken)))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.acceptInvite(inviteToken, "충분히-길고-안전한-비밀번호"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 짧은_비밀번호는_받지_않는다() {
        assertThatThrownBy(() -> service.acceptInvite("invite-token", "짧다"))
                .isInstanceOf(CustomException.class);
        // 계정을 찾아보기도 전에 막는다.
        org.mockito.Mockito.verifyNoInteractions(accountRepository);
    }

    @Test
    void 없는_계정과_틀린_비밀번호를_같은_응답으로_돌려준다() {
        // 나눠서 알려주면 어떤 주소가 등록돼 있는지 확인하는 통로가 된다.
        AdminAccount active = activeAccount("맞는-비밀번호입니다");
        lenient().when(accountRepository.findByEmail("a@example.com"))
                .thenReturn(Optional.of(active));
        lenient().when(accountRepository.findByEmail("none@example.com"))
                .thenReturn(Optional.empty());

        Throwable wrongPassword = org.assertj.core.api.Assertions
                .catchThrowable(() -> service.signIn("a@example.com", "틀린-비밀번호입니다"));
        Throwable noAccount = org.assertj.core.api.Assertions
                .catchThrowable(() -> service.signIn("none@example.com", "아무-비밀번호입니다"));

        assertThat(wrongPassword).isInstanceOf(CustomException.class);
        assertThat(noAccount).isInstanceOf(CustomException.class);
        assertThat(wrongPassword.getMessage()).isEqualTo(noAccount.getMessage());
    }

    @Test
    void 없는_계정으로_로그인해도_비밀번호_대조를_거친다() {
        // 문구만 같게 해서는 부족하다. 계정이 없다고 곧바로 돌려보내면 응답이
        // 눈에 띄게 빨라서, 시간을 재는 것만으로 어떤 주소가 등록돼 있는지 알 수
        // 있다. 시간을 재는 시험은 흔들리므로 대조를 거쳤는지로 본다.
        when(accountRepository.findByEmail("none@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.signIn("none@example.com", "아무-비밀번호입니다"))
                .isInstanceOf(CustomException.class);

        assertThat(matchCalls.get())
                .as("대조를 건너뛰면 응답이 빨라져 등록된 주소가 드러난다")
                .isEqualTo(1);
    }

    @Test
    void 비밀번호를_정하지_않은_계정도_대조를_거친다() {
        // 초대만 받고 아직 비밀번호가 없는 계정이다. 여기서 곧바로 돌려보내면
        // 등록은 됐지만 아직 안 쓰는 주소가 어느 것인지 갈린다.
        AdminAccount invited = invitedAccount("invite-token", NOW.plusSeconds(3600));
        when(accountRepository.findByEmail("a@example.com")).thenReturn(Optional.of(invited));

        assertThatThrownBy(() -> service.signIn("a@example.com", "아무-비밀번호입니다"))
                .isInstanceOf(CustomException.class);

        assertThat(matchCalls.get())
                .as("대조를 건너뛰면 응답이 빨라져 등록된 주소가 드러난다")
                .isEqualTo(1);
    }

    @Test
    void 비밀번호를_정하지_않은_계정은_로그인할_수_없다() {
        AdminAccount invited = invitedAccount("invite-token", NOW.plusSeconds(3600));
        when(accountRepository.findByEmail("a@example.com")).thenReturn(Optional.of(invited));

        assertThatThrownBy(() -> service.signIn("a@example.com", "아무-비밀번호입니다"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 막힌_계정은_로그인할_수_없다() {
        AdminAccount account = activeAccount("맞는-비밀번호입니다");
        account.disable();
        when(accountRepository.findByEmail("a@example.com")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.signIn("a@example.com", "맞는-비밀번호입니다"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 로그인하면_토큰을_주고_시각을_남긴다() {
        AdminAccount account = activeAccount("맞는-비밀번호입니다");
        when(accountRepository.findByEmail("a@example.com")).thenReturn(Optional.of(account));

        String token = service.signIn("a@example.com", "맞는-비밀번호입니다");

        assertThat(token).isNotBlank();
        assertThat(account.getLastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void 서명_키가_없으면_로그인이_열리지_않는다() {
        properties.setJwtSecret("");
        AdminAccountService disabled = new AdminAccountService(
                accountRepository,
                new AdminTokenProvider(properties, Clock.fixed(NOW, ZoneOffset.UTC)),
                passwordEncoder,
                hmacHasher,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> disabled.signIn("a@example.com", "아무-비밀번호입니다"))
                .isInstanceOf(CustomException.class);
        // 계정을 찾아보지도 않는다.
        org.mockito.Mockito.verifyNoInteractions(accountRepository);
    }

    private AdminAccount invitedAccount(String inviteToken, Instant expiresAt) {
        return AdminAccount.invite(
                "a@example.com",
                "나혜",
                AdminRole.PRODUCTION,
                hmacHasher.hash(inviteToken),
                expiresAt
        );
    }

    private AdminAccount activeAccount(String password) {
        AdminAccount account = AdminAccount.invite(
                "a@example.com",
                "종무",
                AdminRole.ADMIN,
                hmacHasher.hash("invite"),
                NOW.plusSeconds(3600)
        );
        account.activate(passwordEncoder.encode(password));
        return account;
    }

    @Test
    void 초대를_다시_보내면_앞서_보낸_링크는_쓸_수_없다() {
        AdminAccount account = invitedAccount("old-token", NOW.plusSeconds(3600));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        String reissued = service.reinvite(1L);

        assertThat(account.getInviteTokenHash()).isEqualTo(hmacHasher.hash(reissued));
        assertThat(account.getInviteTokenHash()).isNotEqualTo(hmacHasher.hash("old-token"));
    }

    @Test
    void 같은_주소로_두_번_초대하지_않는다() {
        when(accountRepository.findByEmail("a@example.com"))
                .thenReturn(Optional.of(activeAccount("맞는-비밀번호입니다")));

        assertThatThrownBy(() -> service.invite("a@example.com", "나혜", AdminRole.PRODUCTION))
                .isInstanceOf(CustomException.class);
        verify(accountRepository, org.mockito.Mockito.never()).save(any());
    }
}
