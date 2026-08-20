package com.pawever.backend.admin.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가린 값에서 원래 값이 드러나지 않는지 본다.
 *
 * 조금씩 남기는 것이 목적이라 어디까지 남길지가 미묘하다. 너무 남기면 가린
 * 뜻이 없고, 다 가리면 목록에서 주문을 가려낼 수 없다.
 */
class PersonalDataMaskTest {

    @Test
    void 이름은_성만_남긴다() {
        assertThat(PersonalDataMask.name("김포에버")).isEqualTo("김***");
        assertThat(PersonalDataMask.name("이종무")).isEqualTo("이**");
    }

    @Test
    void 한_글자_이름은_그대로_둔다() {
        // 한 글자를 가리면 아무것도 남지 않아 주문을 가려낼 수 없다.
        assertThat(PersonalDataMask.name("김")).isEqualTo("김");
    }

    @Test
    void 빈_이름에도_터지지_않는다() {
        assertThat(PersonalDataMask.name(null)).isEmpty();
        assertThat(PersonalDataMask.name("   ")).isEmpty();
    }

    @Test
    void 연락처는_앞뒤만_남긴다() {
        assertThat(PersonalDataMask.phone("01012345678")).isEqualTo("010-12**-56**");
        // 하이픈이 있어도 같은 결과여야 한다. 저장 형식이 섞여 있다.
        assertThat(PersonalDataMask.phone("010-1234-5678")).isEqualTo("010-12**-56**");
    }

    @Test
    void 가린_연락처로_원래_번호를_복원할_수_없다() {
        // 가운데 네 자리 중 두 자리, 뒤 네 자리 중 두 자리가 가려진다.
        String masked = PersonalDataMask.phone("01012345678");

        assertThat(masked).doesNotContain("34");
        assertThat(masked).doesNotContain("78");
        assertThat(masked).contains("*");
    }

    @Test
    void 주소는_시군구까지만_남긴다() {
        assertThat(PersonalDataMask.address("서울특별시 노원구 공릉로 232"))
                .isEqualTo("서울특별시 노원구 ***");
        // 배송 권역을 가늠하는 데는 이것으로 족하다.
        assertThat(PersonalDataMask.address("경기도 성남시 분당구 판교역로 4"))
                .isEqualTo("경기도 성남시 ***");
    }

    @Test
    void 짧은_주소도_뒤를_가린다() {
        assertThat(PersonalDataMask.address("서울 강남구")).isEqualTo("서울 ***");
    }

    @Test
    void 빈_값에도_터지지_않는다() {
        assertThat(PersonalDataMask.phone(null)).isEmpty();
        assertThat(PersonalDataMask.address(null)).isEmpty();
    }
}
