package com.pawever.backend.stats.service;

import com.pawever.backend.stats.dto.UserStatsRow;
import com.pawever.backend.stats.repository.AppStatsRepository;
import com.pawever.backend.user.entity.ReferralType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppStatsExportServiceTest {

    @Mock
    private AppStatsRepository repository;

    private AppStatsExportService service;

    @BeforeEach
    void setUp() {
        service = new AppStatsExportService(repository);
    }

    @Test
    void withdrawnMembersAreNotCountedAsCurrentMembers() {
        when(repository.findUserRows()).thenReturn(List.of(
                kakaoUser(LocalDateTime.of(2026, 7, 1, 0, 0)),
                kakaoUser(LocalDateTime.of(2026, 7, 2, 0, 0)),
                withdrawn(LocalDateTime.of(2026, 7, 3, 0, 0), LocalDateTime.of(2026, 7, 20, 0, 0))
        ));

        String csv = service.summaryCsv();

        assertThat(csv).contains("회원,전체 가입,3");
        assertThat(csv).contains("회원,순 회원,2");
        assertThat(csv).contains("회원,탈퇴,1");
    }

    @Test
    void socialLoginWithoutOnboardingIsCountedSeparately() {
        // 소셜 로그인만 해도 users 레코드가 생긴다. 이 사람들을 회원 수에 그대로
        // 섞으면 실제로 서비스를 쓰기 시작한 사람 수를 알 수 없다.
        when(repository.findUserRows()).thenReturn(List.of(
                onboarded(LocalDateTime.of(2026, 7, 1, 0, 0)),
                onboarded(LocalDateTime.of(2026, 7, 2, 0, 0)),
                kakaoUser(LocalDateTime.of(2026, 7, 3, 0, 0)),
                kakaoUser(LocalDateTime.of(2026, 7, 4, 0, 0))
        ));

        String csv = service.summaryCsv();

        assertThat(csv).contains("회원,온보딩 완료,2");
        assertThat(csv).contains("회원,온보딩 미완료,2");
        assertThat(csv).contains("회원,온보딩 완료율(%),50.0");
    }

    @Test
    void signupChannelCountsOnlyLivingMembersBecauseWithdrawalErasesTheChannel() {
        // 탈퇴하면 kakaoId·naverId·appleId가 모두 지워진다(User.withdraw).
        // 전체 가입자로 나누면 채널 합계가 늘 모자라 보인다.
        when(repository.findUserRows()).thenReturn(List.of(
                kakaoUser(LocalDateTime.of(2026, 7, 1, 0, 0)),
                naverUser(LocalDateTime.of(2026, 7, 2, 0, 0)),
                withdrawn(LocalDateTime.of(2026, 7, 3, 0, 0), LocalDateTime.of(2026, 7, 20, 0, 0))
        ));

        String csv = service.summaryCsv();

        assertThat(csv).contains("가입채널,카카오,1");
        assertThat(csv).contains("가입채널,네이버,1");
        assertThat(csv).contains("가입채널,애플,0");
    }

    @Test
    void monthlySignupsAreGroupedInKoreanTime() {
        // created_at은 UTC로 저장된다. 그대로 묶으면 한국 시간 자정 언저리의
        // 가입이 전달로 밀려 월별 수치가 실제와 어긋난다.
        when(repository.findUserRows()).thenReturn(List.of(
                kakaoUser(LocalDateTime.of(2026, 7, 31, 15, 30)),
                kakaoUser(LocalDateTime.of(2026, 7, 31, 14, 30))
        ));

        String csv = service.summaryCsv();

        assertThat(csv).contains("월별가입,2026-08,1");
        assertThat(csv).contains("월별가입,2026-07,1");
    }

    @Test
    void referralPathKeepsUnansweredVisibleAndListsEveryOption() {
        when(repository.findUserRows()).thenReturn(List.of(
                referred(LocalDateTime.of(2026, 7, 1, 0, 0), ReferralType.INSTAGRAM),
                referred(LocalDateTime.of(2026, 7, 2, 0, 0), ReferralType.INSTAGRAM),
                kakaoUser(LocalDateTime.of(2026, 7, 3, 0, 0))
        ));

        String csv = service.summaryCsv();

        assertThat(csv).contains("유입경로,인스타그램,2");
        assertThat(csv).contains("유입경로,미응답,1");
        // 0으로 나온 경로가 빠지면 "물어본 적 없는 것"과 구분되지 않는다.
        assertThat(csv).contains("유입경로,쓰레드,0");
    }

    @Test
    void petsAndActivityCountsComeFromTheirOwnTables() {
        when(repository.findUserRows()).thenReturn(List.of());
        when(repository.countPets()).thenReturn(94L);
        when(repository.countUserPetLinks()).thenReturn(253L);
        when(repository.countPetMissions()).thenReturn(926L);
        when(repository.countMemorialComments()).thenReturn(516L);

        String csv = service.summaryCsv();

        assertThat(csv).contains("반려동물,등록,94");
        assertThat(csv).contains("반려동물,보호자-펫 연결,253");
        assertThat(csv).contains("활동,미션 배정,926");
        assertThat(csv).contains("활동,추모 댓글,516");
    }

    private UserStatsRow kakaoUser(LocalDateTime createdAt) {
        return new UserStatsRow(
                createdAt, null, false,
                "kakao-" + createdAt, null, null,
                null, null, null, null, null
        );
    }

    private UserStatsRow naverUser(LocalDateTime createdAt) {
        return new UserStatsRow(
                createdAt, null, false,
                null, "naver-" + createdAt, null,
                null, null, null, null, null
        );
    }

    private UserStatsRow onboarded(LocalDateTime createdAt) {
        return new UserStatsRow(
                createdAt, null, true,
                "kakao-" + createdAt, null, null,
                null, "20~29", "male", createdAt, null
        );
    }

    private UserStatsRow referred(LocalDateTime createdAt, ReferralType referralType) {
        return new UserStatsRow(
                createdAt, null, true,
                "kakao-" + createdAt, null, null,
                referralType, null, null, null, null
        );
    }

    /** 탈퇴 회원. 개인정보가 파기된 뒤의 모습 그대로 만든다. */
    private UserStatsRow withdrawn(LocalDateTime createdAt, LocalDateTime deletedAt) {
        return new UserStatsRow(
                createdAt, deletedAt, false,
                null, null, null,
                null, null, null, null, null
        );
    }
}
