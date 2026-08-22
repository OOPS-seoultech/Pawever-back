package com.pawever.backend.goodssurvey.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 금액 구성이 스스로 아귀가 맞는지 본다.
 *
 * 정상가·할인액·배송비·청구액을 따로 저장하므로 넷이 어긋날 수 있다. 어긋난
 * 채로 저장되면 결제한 금액과 청구한 금액이 달라지고, 나중에 어느 쪽이 맞는지
 * 가릴 근거가 없어진다.
 *
 * 값은 11차 회의록에서 정한 가격이다.
 *   정가(바로 신청) 29,900 / 설문 참여자 23,900 / 배송비 3,000
 */
class GoodsOrderPricingTest {

    private static final int LIST = 29_900;
    private static final int DISCOUNT = 6_000;
    private static final int SHIPPING = 3_000;

    @Test
    void 청구액은_정상가에서_할인을_빼고_배송비를_더한_값이어야_한다() {
        assertThatThrownBy(() ->
                new GoodsOrderPricing(LIST, DISCOUNT, "설문 참여 할인", SHIPPING, 20_000))
                .hasMessageContaining("청구액");
    }

    @Test
    void 할인이_없으면_정상가에_배송비만_더한다() {
        GoodsOrderPricing pricing = GoodsOrderPricing.listPrice(LIST, SHIPPING);

        assertThat(pricing.paymentAmountKrw()).isEqualTo(32_900);
        assertThat(pricing.shippingFeeKrw()).isEqualTo(SHIPPING);
        assertThat(pricing.discountAmountKrw()).isZero();
        assertThat(pricing.promotionName()).isNull();
    }

    @Test
    void 설문_참여자는_제작비를_깎고_배송비는_그대로_낸다() {
        GoodsOrderPricing pricing =
                GoodsOrderPricing.discounted(LIST, DISCOUNT, "설문 참여 할인", SHIPPING);

        assertThat(pricing.listPriceKrw()).isEqualTo(29_900);
        assertThat(pricing.discountAmountKrw()).isEqualTo(6_000);
        assertThat(pricing.shippingFeeKrw()).isEqualTo(3_000);
        // 23,900 + 3,000
        assertThat(pricing.paymentAmountKrw()).isEqualTo(26_900);
        assertThat(pricing.promotionName()).isEqualTo("설문 참여 할인");
    }

    @Test
    void 할인은_배송비까지_깎지_않는다() {
        // 배송비까지 깎으면 실제로 나가는 비용을 우리가 대신 내는 셈이 된다.
        GoodsOrderPricing pricing =
                GoodsOrderPricing.discounted(LIST, 50_000, "과한 할인", SHIPPING);

        assertThat(pricing.discountAmountKrw()).isEqualTo(LIST);
        assertThat(pricing.paymentAmountKrw()).isEqualTo(SHIPPING);
    }

    @Test
    void 배송비가_없으면_예전처럼_동작한다() {
        // 1차 체험단 100건은 무료였고 배송비도 받지 않았다.
        GoodsOrderPricing pricing = GoodsOrderPricing.listPrice(0, 0);

        assertThat(pricing.paymentAmountKrw()).isZero();
        assertThat(pricing.shippingFeeKrw()).isZero();
    }

    @Test
    void 음수는_받지_않는다() {
        assertThatThrownBy(() -> GoodsOrderPricing.listPrice(LIST, -1))
                .hasMessageContaining("음수");
    }
}
