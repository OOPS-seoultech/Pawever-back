package com.pawever.backend.workflow.postal;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawever.backend.workflow.postal.PostalNameMatcher.Candidate;
import com.pawever.backend.workflow.postal.PostalNameMatcher.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 이름 표기를 이번에 부친 주문과 맞춰 보는 규칙.
 *
 * <p>근거: PRD §13 「최신 자동 진행 규칙」 표. 답이 하나로 확실할 때만 자동으로
 * 넘기고, 조금이라도 갈리면 사람에게 묻는다.
 */
class PostalNameMatcherTest {

    private static final Candidate BORI = new Candidate("PE-2026-000001", "황성욱", "보리", "01811");
    private static final Candidate CHOCO = new Candidate("PE-2026-000002", "김민지", "초코", "06236");

    @Test
    void 보호자_아이_정상_순서에_후보가_하나면_자동으로_넘긴다() {
        var match = PostalNameMatcher.match("황성욱 보리", "01811", List.of(BORI, CHOCO));

        assertThat(match.kind()).isEqualTo(Kind.AUTO);
        assertThat(match.matched()).isEqualTo("PE-2026-000001");
    }

    @Test
    void 아이_보호자_역순도_후보가_하나면_자동으로_넘긴다() {
        // 역순으로 적혀 오는 일이 실제로 있다. 그것만으로 사람을 부를 이유는 없다.
        var match = PostalNameMatcher.match("보리 황성욱", "01811", List.of(BORI, CHOCO));

        assertThat(match.kind()).isEqualTo(Kind.AUTO);
        assertThat(match.matched()).isEqualTo("PE-2026-000001");
    }

    @Test
    void 보호자명_하나만_와도_대응_후보가_하나면_자동으로_넘긴다() {
        var match = PostalNameMatcher.match("황성욱", "01811", List.of(BORI, CHOCO));

        assertThat(match.kind()).isEqualTo(Kind.AUTO);
        assertThat(match.matched()).isEqualTo("PE-2026-000001");
    }

    @Test
    void 아이_이름_하나만_와도_대응_후보가_하나면_자동으로_넘긴다() {
        var match = PostalNameMatcher.match("초코", "06236", List.of(BORI, CHOCO));

        assertThat(match.kind()).isEqualTo(Kind.AUTO);
        assertThat(match.matched()).isEqualTo("PE-2026-000002");
    }

    @Test
    void 한_이름이_보호자와_아이_양쪽_역할로_걸리면_사람에게_묻는다() {
        // 어떤 주문에서는 보호자 이름이고 다른 주문에서는 아이 이름인 경우다.
        var samePersonName = new Candidate("PE-2026-000003", "보호자", "황성욱", "01811");

        var match = PostalNameMatcher.match("황성욱", "01811", List.of(BORI, samePersonName));

        assertThat(match.kind()).isEqualTo(Kind.MODAL);
        assertThat(match.candidates()).containsExactly("PE-2026-000001", "PE-2026-000003");
    }

    @Test
    void 같은_이름으로_후보가_둘_이상이면_사람에게_묻는다() {
        var namesake = new Candidate("PE-2026-000004", "황성욱", "코코", "01811");

        var match = PostalNameMatcher.match("황성욱", "01811", List.of(BORI, namesake));

        assertThat(match.kind()).isEqualTo(Kind.MODAL);
        assertThat(match.candidates()).hasSize(2);
    }

    @Test
    void 동명이인을_우편번호로_몰래_고르지_않는다() {
        // 근거: PRD §13 "단일 이름이 두 후보 이상이면 우편번호로 몰래 한 후보를
        // 선택하지 않는다." 우편번호가 같아도 다른 사람일 수 있다.
        var namesake = new Candidate("PE-2026-000004", "황성욱", "코코", "06236");

        var match = PostalNameMatcher.match("황성욱", "01811", List.of(BORI, namesake));

        assertThat(match.kind()).isEqualTo(Kind.MODAL);
        assertThat(match.matched()).isNull();
    }

    @Test
    void 후보는_하나인데_우편번호가_다르면_확인받는다() {
        var match = PostalNameMatcher.match("황성욱 보리", "99999", List.of(BORI, CHOCO));

        assertThat(match.kind()).isEqualTo(Kind.POSTAL_CODE_MISMATCH);
        assertThat(match.matched()).isNull();
        assertThat(match.reason()).contains("01811").contains("99999");
    }

    @Test
    void 맞는_주문이_없으면_막는다() {
        var match = PostalNameMatcher.match("없는사람", "01811", List.of(BORI, CHOCO));

        assertThat(match.kind()).isEqualTo(Kind.NO_CANDIDATE);
    }

    @Test
    void 비슷한_이름을_알아서_고쳐_주지_않는다() {
        // 근거: development-spec §9 "퍼지 매칭으로 이름을 고치지 않는다."
        // 고쳐 주는 순간 진짜 황성옥의 주문이 사라진다.
        var match = PostalNameMatcher.match("황성옥 보리", "01811", List.of(BORI));

        assertThat(match.kind()).isEqualTo(Kind.NO_CANDIDATE);
    }

    @Test
    void 이름_순서에_따라_다른_주문이_나오면_임의로_고르지_않는다() {
        // 근거: development-spec §9 "역할이 뒤바뀐 두 주문이 있으면 둘 중 하나를
        // 임의 선택하지 않음."
        var swapped = new Candidate("PE-2026-000005", "보리", "황성욱", "01811");

        var match = PostalNameMatcher.match("황성욱 보리", "01811", List.of(BORI, swapped));

        assertThat(match.kind()).isEqualTo(Kind.MODAL);
        assertThat(match.candidates()).containsExactly("PE-2026-000001", "PE-2026-000005");
    }

    @Test
    void 찾는_범위는_건네받은_발송_묶음_안이다() {
        // 근거: PRD §13 "매칭 범위는 OWNER가 선택한 실제 발송 묶음이다.
        // 공개 전체 DB 검색이 아니다."
        var match = PostalNameMatcher.match("황성욱 보리", "01811", List.of(CHOCO));

        assertThat(match.kind()).isEqualTo(Kind.NO_CANDIDATE);
    }
}
