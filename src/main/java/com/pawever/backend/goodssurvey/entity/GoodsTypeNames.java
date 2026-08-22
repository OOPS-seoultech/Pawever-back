package com.pawever.backend.goodssurvey.entity;

import java.util.Map;

/**
 * 굿즈 코드값을 사람이 읽는 이름으로 바꾼다.
 *
 * 코드값만 보면 무엇을 만들어야 하는지 알 수 없다. 관리자 화면에 backplate
 * 라고 떠 있으면 담당자가 매번 무엇인지 되물어야 한다.
 *
 * 굿즈가 늘면 여기에 더한다. 모르는 값은 코드값을 그대로 둔다. 빈칸으로
 * 두면 화면에서 아무것도 안 보이고, 그게 굿즈가 없는 주문처럼 읽힌다.
 */
public final class GoodsTypeNames {

    private static final Map<String, String> NAMES = Map.of(
            "acrylic", "아크릴 얼굴 키링",
            "face", "3D 얼굴 키링",
            "backplate", "뒷판형 3D 얼굴 키링",
            "figure", "3D 전신 피규어",
            "custom", "원하는 형태 직접 제안"
    );

    private GoodsTypeNames() {
    }

    public static String of(String goodsType) {
        if (goodsType == null || goodsType.isBlank()) {
            return "";
        }
        return NAMES.getOrDefault(goodsType, goodsType);
    }

    public static Map<String, String> all() {
        return NAMES;
    }
}
