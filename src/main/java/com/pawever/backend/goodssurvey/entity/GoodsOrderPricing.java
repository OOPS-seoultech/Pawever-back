package com.pawever.backend.goodssurvey.entity;

/**
 * 주문 한 건의 금액 구성.
 *
 * 금액은 서버에서만 정한다. 화면이 보낸 값을 그대로 쓰면 요청을 고쳐 1원짜리
 * 주문을 만들 수 있다. 결제 대행사에 넘길 금액도 이 값을 쓴다.
 *
 * @param listPriceKrw      정상가
 * @param discountAmountKrw 할인액. 없으면 0
 * @param promotionName     적용한 프로모션 이름. 없으면 null
 * @param paymentAmountKrw  실제 청구액
 */
public record GoodsOrderPricing(
        int listPriceKrw,
        int discountAmountKrw,
        String promotionName,
        int paymentAmountKrw
) {

    public GoodsOrderPricing {
        if (discountAmountKrw < 0 || listPriceKrw < 0) {
            throw new IllegalArgumentException("금액은 음수일 수 없습니다.");
        }
        if (paymentAmountKrw != listPriceKrw - discountAmountKrw) {
            throw new IllegalArgumentException("청구액이 정상가에서 할인액을 뺀 값과 다릅니다.");
        }
    }

    /** 할인 없이 정상가 그대로. */
    public static GoodsOrderPricing listPrice(int listPriceKrw) {
        return new GoodsOrderPricing(listPriceKrw, 0, null, listPriceKrw);
    }

    /**
     * 할인을 적용한다.
     *
     * 할인액이 정상가보다 크면 0원 주문이 만들어진다. 결제 대행사는 0원을 받지
     * 않으므로 정상가까지만 깎는다.
     */
    public static GoodsOrderPricing discounted(
            int listPriceKrw,
            int discountAmountKrw,
            String promotionName
    ) {
        int applied = Math.min(discountAmountKrw, listPriceKrw);
        if (applied <= 0) {
            return listPrice(listPriceKrw);
        }
        return new GoodsOrderPricing(
                listPriceKrw,
                applied,
                promotionName,
                listPriceKrw - applied
        );
    }
}
