package com.pawever.backend.workflow.postal;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 우체국 접수 내역 원문을 줄 단위로 읽는다.
 *
 * <p>대표가 우체국에서 받아 온 내역을 그대로 붙여넣는다. 한 건은 두 줄이다 —
 * 첫 줄에 송장번호·요금·우편번호·이름, 둘째 줄에 배송 종류와 무게.
 *
 * <pre>
 * 1234567890123    1,800    01811    황성욱 보리
 * 통상 반송불요 20g
 * </pre>
 *
 * <p>읽지 못한 줄은 조용히 버리지 않는다. 버리면 43건을 붙여넣었는데 41건만
 * 처리되고도 아무도 모른다. 오류 줄로 남겨 화면에 보여 준다.
 *
 * <p>이름은 자르지 않는다. `황성욱 보리`가 보호자+아이인지 아이+보호자인지는
 * 우리 주문과 맞춰 봐야 알 수 있고, 여기서 추측하면 틀린 채로 굳는다.
 */
public final class PostalReceiptParser {

    /** 붙여넣기 한 번에 받을 수 있는 크기. 실수로 다른 파일을 통째로 넣는 것을 막는다. */
    private static final int MAX_INPUT_LENGTH = 2_000_000;

    private static final Pattern ENTRY =
            Pattern.compile("^(\\d{13})[\\t ]+(\\d{1,3}(?:,\\d{3})+|\\d+)[\\t ]+(\\d{5})[\\t ]+(.+)$");
    private static final Pattern SERVICE =
            Pattern.compile("^(통상)\\s+(반송불요|반송요망)\\s+(\\d+(?:\\.\\d+)?)\\s*g$");
    private static final Pattern SPACES = Pattern.compile("[\\s\\p{Z}]+");
    private static final Pattern LINE_BREAK = Pattern.compile("\\r\\n|\\n|\\r");

    private PostalReceiptParser() {}

    /**
     * 읽은 결과.
     *
     * @param receipts 건별로 읽어 낸 내역
     * @param errors 어느 줄을 왜 읽지 못했는지
     */
    public record Result(List<PostalReceipt> receipts, List<LineError> errors) {}

    /**
     * @param code UNRECOGNIZED_LINE(형식이 아닌 줄) / MALFORMED_ENTRY(숫자로 시작하나
     *     형식이 어긋난 줄) / ORPHAN_SERVICE_LINE(앞선 접수 줄 없이 나온 보조행)
     */
    public record LineError(int lineNumber, String rawLine, String code) {}

    /** 이름 표기에서 공백만 고른다. 글자는 바꾸지 않는다. */
    public static String normalizeLabel(String value) {
        if (value == null) return "";
        String nfc = Normalizer.normalize(value, Normalizer.Form.NFC).trim();
        return SPACES.matcher(nfc).replaceAll(" ");
    }

    public static Result parse(String text) {
        if (text == null) return new Result(List.of(), List.of());
        if (text.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("붙여넣은 내용이 너무 큽니다.");
        }

        List<Pending> pendings = new ArrayList<>();
        List<LineError> errors = new ArrayList<>();
        Pending open = null;

        String body = text.startsWith("﻿") ? text.substring(1) : text;
        String[] lines = LINE_BREAK.split(body, -1);

        for (int index = 0; index < lines.length; index++) {
            String raw = lines[index];
            String line = raw.trim();
            int lineNumber = index + 1;
            if (line.isEmpty()) continue;

            Matcher entry = ENTRY.matcher(line);
            if (entry.matches()) {
                if (open != null) pendings.add(open);
                open = new Pending();
                open.lineNumber = lineNumber;
                open.rawLine = raw;
                open.trackingNumber = entry.group(1);
                open.postalCode = entry.group(3);
                open.originalRecipientLabel = entry.group(4);
                open.recipientLabel = normalizeLabel(entry.group(4));
                try {
                    open.postageKrw = Integer.parseInt(entry.group(2).replace(",", ""));
                } catch (NumberFormatException e) {
                    open.postageKrw = 0;
                    open.issues.add("INVALID_POSTAGE");
                }
                if (open.postageKrw < 0) open.issues.add("INVALID_POSTAGE");
                continue;
            }

            Matcher service = SERVICE.matcher(line);
            if (service.matches()) {
                if (open == null) {
                    errors.add(new LineError(lineNumber, raw, "ORPHAN_SERVICE_LINE"));
                } else if (open.service != null) {
                    open.issues.add("DUPLICATE_SERVICE_LINE");
                } else {
                    double grams = Double.parseDouble(service.group(3));
                    open.service =
                            new PostalReceipt.Service(service.group(1), service.group(2), grams);
                    if (!(grams > 0)) open.issues.add("INVALID_WEIGHT");
                }
                continue;
            }

            // 머리글이든 깨진 줄이든 보이게 남긴다. 조용히 버리면 몇 건이
            // 빠졌는지 아무도 모른다.
            if (open != null) {
                pendings.add(open);
                open = null;
            }
            errors.add(
                    new LineError(
                            lineNumber,
                            raw,
                            Character.isDigit(line.charAt(0)) ? "MALFORMED_ENTRY" : "UNRECOGNIZED_LINE"));
        }
        if (open != null) pendings.add(open);

        // 같은 송장번호가 두 번 들어오면 둘 다 표시한다. 어느 쪽이 맞는지는
        // 사람이 원문을 보고 정해야 한다.
        Map<String, List<Pending>> byTracking = new LinkedHashMap<>();
        for (Pending p : pendings) {
            byTracking.computeIfAbsent(p.trackingNumber, k -> new ArrayList<>()).add(p);
        }
        byTracking.values().stream()
                .filter(group -> group.size() > 1)
                .flatMap(List::stream)
                .forEach(p -> p.issues.add("DUPLICATE_TRACKING_IN_INPUT"));

        List<PostalReceipt> receipts = new ArrayList<>();
        for (Pending p : pendings) {
            if (p.service == null) p.issues.add("MISSING_SERVICE_LINE");
            receipts.add(
                    new PostalReceipt(
                            p.lineNumber,
                            p.rawLine,
                            p.trackingNumber,
                            p.postageKrw,
                            p.postalCode,
                            p.recipientLabel,
                            p.originalRecipientLabel,
                            p.service,
                            List.copyOf(p.issues)));
        }
        return new Result(List.copyOf(receipts), List.copyOf(errors));
    }

    /** 보조행을 만날 때까지 채워 나가는 중간 상태. */
    private static final class Pending {
        int lineNumber;
        String rawLine;
        String trackingNumber;
        int postageKrw;
        String postalCode;
        String recipientLabel;
        String originalRecipientLabel;
        PostalReceipt.Service service;
        final List<String> issues = new ArrayList<>();
    }
}
