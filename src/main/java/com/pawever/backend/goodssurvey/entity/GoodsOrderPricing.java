package com.pawever.backend.goodssurvey.entity;

/**
 * 주문 한 건의 금액 구성.
 *
 * 금액은 서버에서만 정한다. 화면이 보낸 값을 그대로 쓰면 요청을 고쳐 1원짜리
 * 주문을 만들 수 있다. 결제 대행사에 넘길 금액도 이 값을 쓴다.
 *
 * 배송비는 화면에서 따로 보여 주고 결제는 한 번에 받는다. 그래서 청구액에
 * 더해 둔다. 나중에 배송비가 바뀌어도 이미 받은 주문의 금액은 그대로여야
 * 하므로, 설정값을 그때그때 읽지 않고 주문에 적는다.
 *
 * @param listPriceKrw      정상가
 * @param discountAmountKrw 할인액. 없으면 0
 * @param promotionName     적용한 프로모션 이름. 없으면 null
 * @param shippingFeeKrw    배송비. 없으면 0
 * @param paymentAmountKrw  실제 청구액
 */
public record GoodsOrderPricing(
        int listPriceKrw,
        int discountAmountKrw,
        String promotionName,
        int shippingFeeKrw,
        int paymentAmountKrw
) {

    public GoodsOrderPricing {
        if (discountAmountKrw < 0 || listPriceKrw < 0 || shippingFeeKrw < 0) {
            throw new IllegalArgumentException("금액은 음수일 수 없습니다.");
        }
        if (paymentAmountKrw != listPriceKrw - discountAmountKrw + shippingFeeKrw) {
            throw new IllegalArgumentException(
                    "청구액이 정상가에서 할인액을 빼고 배송비를 더한 값과 다릅니다.");
        }
    }

    /** 할인 없이 정상가에 배송비만 더한다. */
    public static GoodsOrderPricing listPrice(int listPriceKrw, int shippingFeeKrw) {
        return new GoodsOrderPricing(
                listPriceKrw, 0, null, shippingFeeKrw, listPriceKrw + shippingFeeKrw);
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
            String promotionName,
            int shippingFeeKrw
    ) {
        // 할인은 제작비에만 붙인다. 배송비까지 깎으면 실제로 나가는 비용을
        // 우리가 대신 내는 셈이 된다.
        int applied = Math.min(discountAmountKrw, listPriceKrw);
        if (applied <= 0) {
            return listPrice(listPriceKrw, shippingFeeKrw);
        }
        return new GoodsOrderPricing(
                listPriceKrw,
                applied,
                promotionName,
                shippingFeeKrw,
                listPriceKrw - applied + shippingFeeKrw
        );
    }
}
