package com.pawever.backend.goodssurvey.service;

import java.util.List;

/**
 * 엑셀에서 바로 열리는 CSV를 만든다.
 *
 * 값에 쉼표나 줄바꿈, 따옴표가 섞여 들어와도 칸이 밀리지 않아야 한다.
 * 사연과 주소에는 실제로 셋 다 들어온다.
 */
public final class GoodsSurveyCsv {

    // 엑셀은 BOM이 없으면 UTF-8 한글을 깨뜨린다.
    public static final String BOM = "﻿";

    private GoodsSurveyCsv() {
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        String escaped = value.replace("\"", "\"\"");
        return needsQuote ? "\"" + escaped + "\"" : escaped;
    }

    public static String row(List<String> values) {
        StringBuilder line = new StringBuilder();
        for (int index = 0; index < values.size(); index += 1) {
            if (index > 0) {
                line.append(',');
            }
            line.append(escape(values.get(index)));
        }
        return line.append('\n').toString();
    }

    public static String document(List<String> header, List<List<String>> rows) {
        StringBuilder document = new StringBuilder(BOM);
        document.append(row(header));
        rows.forEach(values -> document.append(row(values)));
        return document.toString();
    }
}
