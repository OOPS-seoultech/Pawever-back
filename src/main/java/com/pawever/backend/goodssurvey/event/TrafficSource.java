package com.pawever.backend.goodssurvey.event;

import tools.jackson.databind.JsonNode;

/**
 * 화면이 보내 온 tracking 덩어리를 사람이 읽을 한 줄로 줄인다.
 *
 * 이 값은 알림에 실려 나간다. 여기서 던지면 알림이 아니라 접수가 위험해질
 * 자리라, 값이 없거나 모양이 다르면 조용히 "직접 유입" 으로 둔다.
 */
public final class TrafficSource {

    private static final String DIRECT = "직접 유입";

    private TrafficSource() {
    }

    public static String describe(JsonNode tracking) {
        if (tracking == null || !tracking.isObject()) {
            return DIRECT;
        }

        // 마지막 접점을 먼저 본다. 무엇을 보고 지금 신청까지 왔는지가
        // 광고를 더 태울지 말지를 가른다.
        String last = fromTouch(tracking.get("lastTouch"));
        if (last != null) {
            return last;
        }
        // 광고를 보고 왔다가 나중에 직접 들어와 신청하는 흐름이 있다.
        // 마지막만 보면 광고가 한 일이 통째로 사라진다.
        String first = fromTouch(tracking.get("firstTouch"));
        if (first != null) {
            return first;
        }

        String entryPath = text(tracking, "entryPath");
        return entryPath == null ? DIRECT : DIRECT + " (" + entryPath + ")";
    }

    private static String fromTouch(JsonNode touch) {
        if (touch == null || !touch.isObject()) {
            return null;
        }
        String source = text(touch, "utm_source");
        if (source != null) {
            String medium = text(touch, "utm_medium");
            return medium == null ? source : source + " / " + medium;
        }
        // utm 없이 광고 식별자만 붙어 오는 경우가 있다. 어디서 왔는지는
        // 그것만으로도 갈린다.
        if (text(touch, "fbclid") != null) {
            return "meta (fbclid)";
        }
        if (text(touch, "gclid") != null) {
            return "google (gclid)";
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString()) {
            return null;
        }
        String text = value.asString().trim();
        return text.isEmpty() ? null : text;
    }
}
