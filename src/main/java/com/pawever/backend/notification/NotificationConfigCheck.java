package com.pawever.backend.notification;

import com.pawever.backend.notification.sms.SmsProperties;
import com.pawever.backend.notification.telegram.TelegramProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 뜰 때 한 번, 알림 설정에 빠진 것이 있으면 이름을 적어 둔다.
 *
 * 값이 하나 비어 있어도 접수는 그대로 되고 알림만 조용히 실패한다. 실제로
 * 예금주 하나가 비어 있어서 입금 안내 문자가 나가지 않았는데, 그것을
 * 알아내려고 서버에 들어가 로그를 뒤져야 했다. 그 사이 신청한 사람은 계좌를
 * 받지 못한 채 기다렸다.
 *
 * 빠진 것은 뜰 때 말한다. 값 자체는 절대 적지 않는다 — 이름만으로 무엇을
 * 채워야 하는지 알 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConfigCheck {

    /**
     * 텔레그램 봇 토큰의 생김새.
     *
     * BotFather 는 "숫자:영문숫자" 로 준다. 앞의 숫자를 빼고 뒷부분만 넣으면
     * 텔레그램은 404 를 준다 — 실제로 그렇게 들어가 알림이 조용히 멈췄다.
     * 없는 값과 잘못 넣은 값은 다른 문제라 따로 말한다.
     */
    private static final String BOT_TOKEN_SHAPE = "\\d+:.+";

    private final SmsProperties sms;
    private final TelegramProperties telegram;

    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        List<String> missing = new ArrayList<>();
        if (blank(sms.getApiKey())) missing.add("ALIGO_API_KEY");
        if (blank(sms.getUserId())) missing.add("ALIGO_USER_ID");
        if (blank(sms.getSender())) missing.add("ALIGO_SENDER");
        if (blank(sms.getBank().getName())) missing.add("GOODS_BANK_NAME");
        if (blank(sms.getBank().getAccount())) missing.add("GOODS_BANK_ACCOUNT");
        if (blank(sms.getBank().getHolder())) missing.add("GOODS_BANK_HOLDER");
        if (blank(telegram.getBotToken())) missing.add("TELEGRAM_BOT_TOKEN");
        if (blank(telegram.getChatId())) missing.add("TELEGRAM_CHAT_ID");

        if (!missing.isEmpty()) {
            log.warn("알림 설정에 빠진 값이 있다. 그 알림은 조용히 실패한다: {}", missing);
        }

        // 채워는 두었는데 모양이 아닌 경우. 없는 것보다 알아채기 어렵다.
        if (!blank(telegram.getBotToken())
                && !telegram.getBotToken().matches(BOT_TOKEN_SHAPE)) {
            log.warn("TELEGRAM_BOT_TOKEN 이 \"숫자:영문숫자\" 모양이 아니다. "
                    + "BotFather 가 준 값 전체를 넣어야 한다");
        }

        if (missing.isEmpty()) {
            log.info("알림 설정 확인: 문자·텔레그램 모두 준비됐다");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
