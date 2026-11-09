package com.pawever.backend.admin.service;

import com.pawever.backend.admin.config.AdminProperties;
import com.pawever.backend.global.exception.CustomException;
import com.pawever.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminInvitationMailer {
    private final ObjectProvider<JavaMailSender> senders;
    private final AdminProperties properties;
    private final Environment environment;

    public void send(String email, String name, String token) {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null || properties.getMailFrom().isBlank()
                || environment.getProperty("spring.mail.host", "").isBlank()) {
            throw new CustomException(ErrorCode.ADMIN_MAIL_NOT_CONFIGURED);
        }
        var message = new SimpleMailMessage();
        message.setFrom(properties.getMailFrom());
        message.setTo(email);
        message.setSubject("[PAW-EVER] 관리자 사이트 담당자로 초대되었습니다");
        message.setText("""
                %s 님, PAW-EVER 관리자 사이트 담당자로 초대되었습니다.

                로그인 이메일: %s
                아래 링크에서 본인만 아는 비밀번호를 5자 이상으로 설정해 주세요.
                대문자·숫자·특수문자 조합은 필요하지 않습니다.

                %s/admin/accept-invite?token=%s

                링크는 7일 동안 한 번만 사용할 수 있습니다.
                비밀번호는 관리자에게 공개되지 않습니다.
                로그인 비밀번호를 10회 연속 틀리면 담당자 계정이 삭제됩니다.
                성공적으로 로그인하면 실패 횟수는 0으로 초기화됩니다.
                본인이 요청하지 않은 초대라면 이 메일을 무시해 주세요.
                """.formatted(name, email, properties.getPublicUrl().replaceAll("/+$", ""), token));
        try {
            sender.send(message);
        } catch (MailException failure) {
            // SMTP 오류에는 수신 주소와 초대 링크가 포함될 수 있어 기록하지 않는다.
            throw new CustomException(ErrorCode.ADMIN_MAIL_SEND_FAILED);
        }
    }
}
