package com.pawever.backend.stats.service;

import com.pawever.backend.goodssurvey.service.GoodsSurveyCsv;
import com.pawever.backend.pet.entity.LifecycleStatus;
import com.pawever.backend.stats.dto.UserStatsRow;
import com.pawever.backend.stats.repository.AppStatsRepository;
import com.pawever.backend.user.entity.ReferralType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * 앱 이용 현황을 CSV 한 장으로 만든다.
 *
 * 세로로 긴 `구분·지표·값` 세 칸이다. 지표마다 열을 만들면 새 지표가 생길 때마다
 * 열이 늘어 예전 파일과 나란히 볼 수 없다. 행으로 쌓으면 그대로 이어 붙는다.
 */
@Service
@RequiredArgsConstructor
public class AppStatsExportService {

    private static final List<String> HEADER = List.of("구분", "지표", "값");

    // DB는 UTC로 저장한다. 사람이 보는 숫자는 한국 시간이어야 한다.
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String UNKNOWN = "미상";
    private static final String UNANSWERED = "미응답";

    /** 코드값만 보면 무엇을 뜻하는지 알 수 없어 화면에서 쓰는 이름으로 바꿔 싣는다. */
    private static final Map<ReferralType, String> REFERRAL_NAMES = Map.of(
            ReferralType.FRIEND, "주변 추천",
            ReferralType.THREADS, "쓰레드",
            ReferralType.INSTAGRAM, "인스타그램",
            ReferralType.OFFLINE, "오프라인 소개",
            ReferralType.OTHER, "기타"
    );

    private final AppStatsRepository repository;

    @Transactional(readOnly = true)
    public String summaryCsv() {
        List<UserStatsRow> users = repository.findUserRows();
        List<UserStatsRow> active = users.stream().filter(UserStatsRow::active).toList();

        List<List<String>> rows = new ArrayList<>();
        appendBasis(rows);
        appendMembers(rows, users, active);
        appendChannels(rows, active);
        appendReferrals(rows, active);
        appendProfile(rows, active);
        appendConsents(rows, active);
        appendPets(rows);
        appendActivity(rows);
        appendMonthlySignups(rows, users);

        return GoodsSurveyCsv.document(HEADER, rows);
    }

    /** 파일만 따로 돌아다녀도 언제 기준인지 알 수 있어야 한다. */
    private void appendBasis(List<List<String>> rows) {
        add(rows, "기준", "집계 시각(KST)", LocalDateTime.now(KST).format(STAMP));
        add(rows, "기준", "시간대", "Asia/Seoul (DB는 UTC로 저장)");
    }

    private void appendMembers(
            List<List<String>> rows,
            List<UserStatsRow> users,
            List<UserStatsRow> active
    ) {
        long onboarded = count(active, UserStatsRow::onboardingComplete);

        add(rows, "회원", "전체 가입", users.size());
        add(rows, "회원", "순 회원", active.size());
        add(rows, "회원", "탈퇴", users.size() - active.size());
        // 소셜 로그인만 해도 레코드가 생긴다. 온보딩을 마쳐야 서비스를 쓰기 시작한 사람이다.
        add(rows, "회원", "온보딩 완료", onboarded);
        add(rows, "회원", "온보딩 미완료", active.size() - onboarded);
        add(rows, "회원", "온보딩 완료율(%)", percent(onboarded, active.size()));
    }

    /**
     * 가입 채널.
     *
     * 탈퇴하면 소셜 ID가 모두 지워지므로(User.withdraw) 순 회원만 센다.
     * 전체 가입자로 나누면 채널 합계가 늘 모자라 보인다.
     * 계정을 여러 개 연결한 회원은 각 채널에 함께 잡힌다.
     */
    private void appendChannels(List<List<String>> rows, List<UserStatsRow> active) {
        add(rows, "가입채널", "카카오", count(active, row -> row.kakaoId() != null));
        add(rows, "가입채널", "네이버", count(active, row -> row.naverId() != null));
        add(rows, "가입채널", "애플", count(active, row -> row.appleId() != null));
    }

    /** 0으로 나온 경로도 남긴다. 빼 버리면 "아무도 안 골랐다"와 "묻지 않았다"가 같아진다. */
    private void appendReferrals(List<List<String>> rows, List<UserStatsRow> active) {
        for (ReferralType type : ReferralType.values()) {
            add(rows, "유입경로", REFERRAL_NAMES.getOrDefault(type, type.name()),
                    count(active, row -> row.referralType() == type));
        }
        add(rows, "유입경로", UNANSWERED, count(active, row -> row.referralType() == null));
    }

    /** 연령대·성별은 소셜에서 받은 값이라 비어 있는 회원이 많다. 빈 값도 한 칸을 차지한다. */
    private void appendProfile(List<List<String>> rows, List<UserStatsRow> active) {
        appendGrouped(rows, "연령대", active.stream().map(UserStatsRow::ageRange).toList());
        appendGrouped(rows, "성별", active.stream().map(UserStatsRow::gender).toList());
    }

    private void appendConsents(List<List<String>> rows, List<UserStatsRow> active) {
        long push = count(active, row -> row.notificationAgreedAt() != null);
        long marketing = count(active, row -> row.marketingAgreedAt() != null);

        add(rows, "동의", "푸시 알림 동의", push);
        add(rows, "동의", "푸시 알림 동의율(%)", percent(push, active.size()));
        add(rows, "동의", "마케팅 수신 동의", marketing);
        add(rows, "동의", "마케팅 수신 동의율(%)", percent(marketing, active.size()));
        // 동의했어도 토큰이 없으면 실제로는 도달하지 않는다.
        add(rows, "동의", "푸시 토큰 보유", repository.countUsersWithPushToken());
    }

    private void appendPets(List<List<String>> rows) {
        add(rows, "반려동물", "등록", repository.countPets());
        add(rows, "반려동물", "이별 전", repository.countPetsByLifecycleStatus(LifecycleStatus.BEFORE_FAREWELL));
        add(rows, "반려동물", "이별 후", repository.countPetsByLifecycleStatus(LifecycleStatus.AFTER_FAREWELL));
        add(rows, "반려동물", "응급 모드", repository.countPetsInEmergencyMode());
        add(rows, "반려동물", "보호자-펫 연결", repository.countUserPetLinks());
        add(rows, "반려동물", "펫 보유 회원", repository.countUsersWithPet());
    }

    private void appendActivity(List<List<String>> rows) {
        add(rows, "활동", "미션 배정", repository.countPetMissions());
        add(rows, "활동", "미션 완료", repository.countCompletedPetMissions());
        add(rows, "활동", "추모 댓글", repository.countMemorialComments());
        add(rows, "활동", "응급 진행", repository.countEmergencyProgresses());
        add(rows, "활동", "이별 준비 진행", repository.countFarewellPreviewProgresses());
        add(rows, "활동", "서비스 리뷰", repository.countServiceReviews());
    }

    /** 탈퇴자도 가입한 달에 함께 센다. 그 달에 실제로 들어온 사람 수이기 때문이다. */
    private void appendMonthlySignups(List<List<String>> rows, List<UserStatsRow> users) {
        Map<String, Long> monthly = new TreeMap<>();
        users.forEach(row -> monthly.merge(koreanMonth(row.createdAt()), 1L, Long::sum));
        monthly.forEach((month, count) -> add(rows, "월별가입", month, count));
    }

    private String koreanMonth(LocalDateTime createdAtUtc) {
        if (createdAtUtc == null) {
            return UNKNOWN;
        }
        return YearMonth.from(createdAtUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(KST)).toString();
    }

    /** 값 종류를 미리 알 수 없는 항목. 나온 값만 세고 빈 값은 한 칸으로 모은다. */
    private void appendGrouped(List<List<String>> rows, String group, List<String> values) {
        Map<String, Long> counted = new TreeMap<>();
        values.forEach(value -> counted.merge(blankToUnknown(value), 1L, Long::sum));
        counted.forEach((value, count) -> add(rows, group, value, count));
    }

    private String blankToUnknown(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }

    private long count(List<UserStatsRow> rows, Predicate<UserStatsRow> condition) {
        return rows.stream().filter(condition).count();
    }

    private String percent(long part, long total) {
        if (total == 0) {
            return "0.0";
        }
        return String.format(Locale.ROOT, "%.1f", 100.0 * part / total);
    }

    private void add(List<List<String>> rows, String group, String label, Object value) {
        rows.add(List.of(group, label, String.valueOf(value)));
    }
}
