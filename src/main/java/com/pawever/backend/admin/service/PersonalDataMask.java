package com.pawever.backend.admin.service;

/**
 * 목록 화면과 사내 알림에 쓰는 가림 처리.
 *
 * 주문을 훑어보는 데 전체 연락처나 주소가 필요하지 않다. 목록에 그대로 두면
 * 화면을 켜 두는 것만으로 어깨너머와 화면 공유에 고객 정보가 흘러간다.
 * 전체가 필요하면 상세에서 열고, 그 사실은 이력에 남는다.
 */
public final class PersonalDataMask {

    private PersonalDataMask() {
    }

    /** 김포에버 → 김**. 성만 남긴다. */
    public static String name(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() == 1) {
            return trimmed;
        }
        return trimmed.charAt(0) + "*".repeat(trimmed.length() - 1);
    }

    /**
     * 01012345678 → 010-12**-56**.
     *
     * 국번과 뒷자리의 앞 두 자리만 남긴다. 문서 6-3의 형식이다. 다 가리면
     * 고객이 알려준 번호와 목록을 대조할 수 없고, 다 남기면 가린 뜻이 없다.
     */
    public static String phone(String value) {
        if (value == null) {
            return "";
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() < 9) {
            return "*".repeat(Math.max(digits.length(), 1));
        }
        int tailLength = 4;
        int middleLength = digits.length() - 3 - tailLength;
        String head = digits.substring(0, 3);
        String middle = digits.substring(3, 3 + middleLength);
        String tail = digits.substring(3 + middleLength);
        return head + "-" + halfMasked(middle) + "-" + halfMasked(tail);
    }

    /** 앞 두 자리만 남기고 나머지를 가린다. */
    private static String halfMasked(String group) {
        if (group.length() <= 2) {
            return group;
        }
        return group.substring(0, 2) + "*".repeat(group.length() - 2);
    }

    /**
     * 서울특별시 노원구 공릉로 232 → 서울특별시 노원구 ***.
     *
     * 어느 지역인지까지만 남긴다. 배송 권역을 가늠하는 데는 그것으로 족하고,
     * 문 앞이 어디인지는 목록에서 알 필요가 없다.
     */
    public static String address(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] parts = value.trim().split("\s+");
        if (parts.length <= 2) {
            return parts[0] + " ***";
        }
        return parts[0] + " " + parts[1] + " ***";
    }
}
