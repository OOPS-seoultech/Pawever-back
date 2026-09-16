package com.pawever.backend.workflow.postal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 우체국 내역의 이름 표기를 이번에 부친 주문과 맞춰 본다.
 *
 * <p>우체국 내역에는 주문번호가 없다. 이름 한 덩어리뿐이고, 그 이름이 보호자인지
 * 아이인지, 어느 순서인지 제각각이다. 그래서 답이 하나로 확실할 때만 자동으로
 * 넘기고 조금이라도 갈리면 사람에게 묻는다. 잘못 맞추면 다른 고객에게 다른
 * 피규어가 간다.
 *
 * <p>비슷한 이름을 알아서 고치지 않는다. 공백만 고르고 글자는 그대로 둔다 —
 * `황성옥`을 `황성욱`으로 바꿔 주는 순간, 진짜 `황성옥`의 주문이 사라진다.
 *
 * <p>찾는 범위는 대표가 고른 발송 묶음 안이다. 전체 주문을 이름으로 뒤지지 않는다.
 */
public final class PostalNameMatcher {

    private PostalNameMatcher() {}

    /** 발송 묶음에 들어 있는 주문 하나. */
    public record Candidate(String orderNumber, String guardianName, String petName, String postalCode) {}

    /**
     * 맞춰 본 결과.
     *
     * <ul>
     *   <li>{@code AUTO} — 답이 하나다. 모달 없이 연결 후보로 둔다
     *   <li>{@code MODAL} — 후보가 여럿이거나 역할이 엇갈린다. 사람이 고른다
     *   <li>{@code POSTAL_CODE_MISMATCH} — 후보는 하나인데 우편번호가 다르다. 확인받는다
     *   <li>{@code NO_CANDIDATE} — 맞는 주문이 없다. 막는다
     * </ul>
     */
    public enum Kind {
        AUTO,
        MODAL,
        POSTAL_CODE_MISMATCH,
        NO_CANDIDATE
    }

    /**
     * @param matched AUTO 일 때만 채워진다. 그 밖에는 null
     * @param candidates 사람에게 보여 줄 후보들
     * @param reason 왜 이렇게 판단했는지. 화면과 감사 기록이 함께 쓴다
     */
    public record Match(Kind kind, String matched, List<String> candidates, String reason) {}

    public static Match match(String recipientLabel, String postalCode, List<Candidate> pool) {
        String label = PostalReceiptParser.normalizeLabel(recipientLabel);
        if (label.isEmpty()) {
            return new Match(Kind.NO_CANDIDATE, null, List.of(), "이름 표기가 비어 있습니다.");
        }

        String[] parts = label.split(" ");

        // 이름이 둘이면 보호자+아이 순서와 아이+보호자 역순을 함께 본다.
        // 역순으로 적혀 오는 일이 실제로 있고, 그것만으로 사람을 부를 이유는 없다.
        if (parts.length == 2) {
            Set<String> normal = orderNumbersOf(pool, c -> eq(c.guardianName(), parts[0]) && eq(c.petName(), parts[1]));
            Set<String> reversed = orderNumbersOf(pool, c -> eq(c.petName(), parts[0]) && eq(c.guardianName(), parts[1]));
            Set<String> both = new LinkedHashSet<>(normal);
            both.addAll(reversed);
            if (!both.isEmpty()) {
                // 정상 순서와 역순이 서로 다른 주문을 가리키면 둘 중 하나를
                // 임의로 고르지 않는다.
                String why =
                        !normal.isEmpty() && !reversed.isEmpty() && both.size() > 1
                                ? "이름 순서에 따라 다른 주문이 나옵니다."
                                : "이름 두 개가 여러 주문과 맞습니다.";
                return decide(both, postalCode, pool, why);
            }
        }

        // 이름이 하나면 보호자 이름과 아이 이름 양쪽에서 찾는다. 이름 표기가
        // 통째로 한 칸에 들어간 경우도 여기서 걸린다.
        Set<String> single = orderNumbersOf(pool, c -> eq(c.guardianName(), label) || eq(c.petName(), label));
        if (!single.isEmpty()) {
            // 한 이름이 어떤 주문에서는 보호자, 다른 주문에서는 아이인 경우다.
            return decide(single, postalCode, pool, "같은 이름이 여러 주문과 맞습니다.");
        }

        return new Match(Kind.NO_CANDIDATE, null, List.of(), "이 발송 묶음에 맞는 주문이 없습니다.");
    }

    /**
     * 후보 수와 우편번호를 보고 자동으로 넘길지 정한다.
     *
     * <p>후보가 둘 이상이면 우편번호로 몰래 하나를 고르지 않는다. 우편번호가 같아도
     * 다른 사람일 수 있고, 그렇게 고른 것은 사람이 확인한 적이 없다.
     */
    private static Match decide(Set<String> candidates, String postalCode, List<Candidate> pool, String why) {
        List<String> list = List.copyOf(candidates);
        if (list.size() > 1) {
            return new Match(Kind.MODAL, null, list, why);
        }
        String only = list.get(0);
        String expected =
                pool.stream()
                        .filter(c -> c.orderNumber().equals(only))
                        .map(Candidate::postalCode)
                        .findFirst()
                        .orElse(null);
        if (expected != null && postalCode != null && !expected.equals(postalCode)) {
            return new Match(
                    Kind.POSTAL_CODE_MISMATCH,
                    null,
                    list,
                    "우편번호가 다릅니다. 주문 " + expected + " / 접수 " + postalCode);
        }
        return new Match(Kind.AUTO, only, list, "이름과 우편번호가 맞는 주문이 하나입니다.");
    }

    private static Set<String> orderNumbersOf(List<Candidate> pool, java.util.function.Predicate<Candidate> test) {
        Set<String> found = new LinkedHashSet<>();
        for (Candidate c : pool) if (test.test(c)) found.add(c.orderNumber());
        return found;
    }

    /** 공백만 고른 뒤 그대로 비교한다. 비슷하다고 같다고 보지 않는다. */
    private static boolean eq(String stored, String given) {
        if (stored == null || given == null) return false;
        return PostalReceiptParser.normalizeLabel(stored).equals(PostalReceiptParser.normalizeLabel(given));
    }

    /** 사람이 고른 뒤에도 쓰는, 후보 목록을 사람이 읽을 수 있게 만드는 도구. */
    public static List<String> describe(List<String> orderNumbers, List<Candidate> pool) {
        List<String> described = new ArrayList<>();
        for (String orderNumber : orderNumbers) {
            pool.stream()
                    .filter(c -> c.orderNumber().equals(orderNumber))
                    .findFirst()
                    .ifPresent(c -> described.add(c.orderNumber() + " " + c.guardianName() + "/" + c.petName()));
        }
        return described;
    }
}
