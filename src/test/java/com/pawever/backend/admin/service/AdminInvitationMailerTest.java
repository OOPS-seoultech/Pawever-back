package com.pawever.backend.admin.service;

import com.pawever.backend.admin.config.AdminProperties;
import com.pawever.backend.global.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminInvitationMailerTest {
    @Test
    void 초대메일에_본인이메일과_일회용링크_비밀번호정책을_담는다() {
        var sender = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        var properties = new AdminProperties();
        properties.setMailFrom("team@example.test");
        var mailer = new AdminInvitationMailer(provider, properties, new MockEnvironment().withProperty("spring.mail.host", "smtp.example.test"));
        mailer.send("worker@example.test", "직원", "one-use-token");
        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("worker@example.test");
        assertThat(message.getValue().getText()).contains("worker@example.test", "5자 이상", "/admin/accept-invite?token=one-use-token", "10회 연속");
    }

    @Test
    void 미설정이면_발송성공으로_처리하지_않는다() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        var mailer = new AdminInvitationMailer(provider, new AdminProperties(), new MockEnvironment());
        assertThatThrownBy(() -> mailer.send("worker@example.test", "직원", "token")).isInstanceOf(CustomException.class);
    }
}
