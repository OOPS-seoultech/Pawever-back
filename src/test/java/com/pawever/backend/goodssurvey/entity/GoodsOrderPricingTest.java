package com.pawever.backend.goodssurvey.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 금액 구성이 스스로 아귀가 맞는지 본다.
 *
 * 정상가·할인액·청구액을 따로 저장하므로 셋이 어긋날 수 있다. 어긋난 채로
 * 저장되면 결제한 금액과 청구한 금액이 달라지고, 나중에 어느 쪽이 맞는지
 * 가릴 근거가 없어진다.
 */
class GoodsOrderPricingTest {

    @Test
    void 청구액은_정상가에서_할인액을_뺀_값이어야_한다() {
        assertThatThrownBy(() -> new GoodsOrderPricing(29_900, 5_000, "설문 참여 할인", 20_000))
                .hasMessageContaining("청구액");
    }

    @Test
    void 할인이_없으면_정상가를_그대로_청구한다() {
        GoodsOrderPricing pricing = GoodsOrderPricing.listPrice(29_900);

        assertThat(pricing.paymentAmountKrw()).isEqualTo(29_900);
        assertThat(pricing.discountAmountKrw()).isZero();
        assertThat(pricing.promotionName()).isNull();
    }

    @Test
    void 할인을_적용하면_이름과_함께_남는다() {
        GoodsOrderPricing pricing =
                GoodsOrderPricing.discounted(29_900, 5_000, "설문 참여 할인");

        assertThat(pricing.listPriceKrw()).isEqualTo(29_900);
        assertThat(pricing.discountAmountKrw()).isEqualTo(5_000);
        assertThat(pricing.paymentAmountKrw()).isEqualTo(24_900);
        assertThat(pricing.promotionName()).isEqualTo("설문 참여 할인");
    }

    @Test
    void 할인액이_정상가보다_커도_0원_주문이_되지_않는다() {
        // 결제 대행사는 0원을 받지 않는다. 여기서 막지 않으면 결제창에서 터진다.
        GoodsOrderPricing pricing =
                GoodsOrderPricing.discounted(29_900, 50_000, "과한 할인");

        assertThat(pricing.paymentAmountKrw()).isZero();
        assertThat(pricing.discountAmountKrw()).isEqualTo(29_900);
    }
}
