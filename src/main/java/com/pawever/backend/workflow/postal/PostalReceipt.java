package com.pawever.backend.workflow.postal;

import java.util.List;

/**
 * 우체국 접수 내역 한 건.
 *
 * <p>우체국이 돌려주는 종이·파일에는 주문번호가 없다. 있는 것은 송장번호, 요금,
 * 우편번호, 그리고 받는 사람 이름 표기뿐이다. 그래서 이 값들을 고치거나 추측하지
 * 않고 원문 그대로 들고 다닌다.
 *
 * @param trackingNumber 13자리 우편물번호. 반드시 문자열이다 — 숫자로 바꾸면 앞자리
 *     0이 사라지고, 사라진 번호는 다시 만들 수 없다
 * @param postalCode 5자리 우편번호. 같은 이유로 문자열이다
 * @param recipientLabel 공백만 고른 이름 표기. 어디까지가 보호자이고 어디부터가 아이
 *     이름인지 여기서 자르지 않는다
 * @param originalRecipientLabel 손대지 않은 원문 이름 표기
 * @param issues 이 줄에서 발견한 문제. 있다고 버리지 않고 화면에 보여 준다
 */
public record PostalReceipt(
        int lineNumber,
        String rawLine,
        String trackingNumber,
        int postageKrw,
        String postalCode,
        String recipientLabel,
        String originalRecipientLabel,
        Service service,
        List<String> issues) {

    /** 보조행. `통상 반송불요 20g` 같은 줄에서 읽는다. */
    public record Service(String category, String returnPolicy, double weightGrams) {}

    /** 문제가 하나라도 있으면 사람이 확인해야 한다. */
    public boolean hasIssues() {
        return !issues.isEmpty();
    }
}
