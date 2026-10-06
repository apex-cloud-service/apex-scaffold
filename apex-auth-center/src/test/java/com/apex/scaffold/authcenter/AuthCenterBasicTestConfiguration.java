package com.apex.scaffold.authcenter;

import static org.mockito.Mockito.mock;

import com.apex.scaffold.domain.service.SmsCodeService;
import com.apex.scaffold.infrastructure.mail.MailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class AuthCenterBasicTestConfiguration {

    @Bean
    @Primary
    SmsCodeService smsCodeService() {
        return mock(SmsCodeService.class);
    }

    @Bean
    @Primary
    MailService mailService() {
        return mock(MailService.class);
    }
}
