package com.roots.mms.service.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpEmailServiceTest {

    private JavaMailSender mailSender;
    private SmtpEmailService service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        service = new SmtpEmailService(mailSender);
        ReflectionTestUtils.setField(service, "fromAddress", "noreply@test.com");
    }

    @Test
    void send_callsMailSenderWithCorrectRecipient() {
        OutgoingEmail email = OutgoingEmail.of("user@example.com", "Hello", "Body text");

        service.send(email);

        ArgumentCaptor<SimpleMailMessage> captor = forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("user@example.com");
    }

    @Test
    void send_usesConfiguredFromAddress() {
        OutgoingEmail email = OutgoingEmail.of("user@example.com", "Hello", "Body text");

        service.send(email);

        ArgumentCaptor<SimpleMailMessage> captor = forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getFrom()).isEqualTo("noreply@test.com");
    }

    @Test
    void send_setsSubject() {
        OutgoingEmail email = OutgoingEmail.of("user@example.com", "Reset your password", "Body");

        service.send(email);

        ArgumentCaptor<SimpleMailMessage> captor = forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("Reset your password");
    }

    @Test
    void send_setsBody() {
        OutgoingEmail email = OutgoingEmail.of("user@example.com", "Subject", "Click here to reset.");

        service.send(email);

        ArgumentCaptor<SimpleMailMessage> captor = forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getText()).isEqualTo("Click here to reset.");
    }

    @Test
    void send_mailSenderThrows_isSwallowedNotPropagated() {
        // send() is called from fire-and-forget flows (signup, forgot-password)
        // that must not fail the caller's request just because SMTP is down —
        // the failure is logged with the SMTP root cause instead of thrown.
        doThrow(new org.springframework.mail.MailSendException("SMTP unavailable"))
                .when(mailSender).send((SimpleMailMessage) org.mockito.ArgumentMatchers.any());
        OutgoingEmail email = OutgoingEmail.of("user@example.com", "Sub", "Body");

        assertThatCode(() -> service.send(email)).doesNotThrowAnyException();
    }

    @Test
    void send_defaultFromAddress_usedWhenNotOverridden() {
        SmtpEmailService defaultService = new SmtpEmailService(mailSender);
        ReflectionTestUtils.setField(defaultService, "fromAddress", "no-reply@roots-mms.local");

        defaultService.send(OutgoingEmail.of("x@example.com", "s", "b"));

        ArgumentCaptor<SimpleMailMessage> captor = forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getFrom()).isEqualTo("no-reply@roots-mms.local");
    }
}
