package com.apex.scaffold.auth.infrastructure.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.auth.domain.model.entity.PasswordRecoveryEntity;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.infrastructure.chuanglan.service.ChuanglanApiService;
import com.apex.scaffold.infrastructure.mail.MailService;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 密码找回验证码投递实现单元测试。
 *
 * @author Apex Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("恢复验证码投递测试")
class RecoveryChallengeSenderImplTest {

    private static final String PHONE = "13800138000";

    private static final String CODE = "123456";

    @Mock
    private ObjectProvider<MailService> mailServiceProvider;

    @Mock
    private ObjectProvider<ChuanglanApiService> chuanglanApiServiceProvider;

    @Mock
    private ChuanglanApiService chuanglanApiService;

    private RecoveryChallengeSenderImpl sender() {
        return new RecoveryChallengeSenderImpl(mailServiceProvider, chuanglanApiServiceProvider);
    }

    @Test
    @DisplayName("SMS：创蓝返回 000000 时投递成功")
    void smsSuccess() {
        when(chuanglanApiServiceProvider.getIfAvailable()).thenReturn(chuanglanApiService);
        when(chuanglanApiService.sendSms(PHONE, CODE)).thenReturn(Mono.just(response("000000")));

        StepVerifier.create(sender().send(PasswordRecoveryEntity.CHANNEL_SMS, PHONE, CODE))
                .verifyComplete();

        verify(chuanglanApiService).sendSms(PHONE, CODE);
    }

    @Test
    @DisplayName("SMS：返回码非 000000 时以短信服务错误终止")
    void smsNonSuccessCode() {
        when(chuanglanApiServiceProvider.getIfAvailable()).thenReturn(chuanglanApiService);
        when(chuanglanApiService.sendSms(PHONE, CODE)).thenReturn(Mono.just(response("100001")));

        StepVerifier.create(sender().send(PasswordRecoveryEntity.CHANNEL_SMS, PHONE, CODE))
                .expectErrorMatches(RecoveryChallengeSenderImplTest::isSmsServiceError)
                .verify();
    }

    @Test
    @DisplayName("SMS：返回体缺少结果码时按失败处理")
    void smsMissingResultCode() {
        when(chuanglanApiServiceProvider.getIfAvailable()).thenReturn(chuanglanApiService);
        when(chuanglanApiService.sendSms(PHONE, CODE)).thenReturn(Mono.just(new HashMap<>()));

        StepVerifier.create(sender().send(PasswordRecoveryEntity.CHANNEL_SMS, PHONE, CODE))
                .expectErrorMatches(RecoveryChallengeSenderImplTest::isSmsServiceError)
                .verify();
    }

    @Test
    @DisplayName("SMS：短信服务未装配时按不可用处理")
    void smsServiceAbsent() {
        when(chuanglanApiServiceProvider.getIfAvailable()).thenReturn(null);

        StepVerifier.create(sender().send(PasswordRecoveryEntity.CHANNEL_SMS, PHONE, CODE))
                .expectErrorMatches(RecoveryChallengeSenderImplTest::isSmsServiceError)
                .verify();

        verifyNoInteractions(chuanglanApiService);
    }

    @Test
    @DisplayName("SMS：下游抛出的技术异常包装为短信服务错误")
    void smsDownstreamFailureWrapped() {
        when(chuanglanApiServiceProvider.getIfAvailable()).thenReturn(chuanglanApiService);
        when(chuanglanApiService.sendSms(PHONE, CODE))
                .thenReturn(Mono.error(new IllegalStateException("connect timed out")));

        StepVerifier.create(sender().send(PasswordRecoveryEntity.CHANNEL_SMS, PHONE, CODE))
                .expectErrorMatches(RecoveryChallengeSenderImplTest::isSmsServiceError)
                .verify();
    }

    @Test
    @DisplayName("EMAIL：投递调用邮件服务")
    void emailSuccess() {
        MailService mailService = mock(MailService.class);
        when(mailServiceProvider.getIfAvailable()).thenReturn(mailService);

        StepVerifier.create(sender().send(PasswordRecoveryEntity.CHANNEL_EMAIL, "a@b.com", CODE))
                .verifyComplete();

        verify(mailService).sendSimple(eq("a@b.com"), anyString(), contains(CODE));
    }

    @Test
    @DisplayName("EMAIL：邮件服务未装配时按不可用处理")
    void emailServiceAbsent() {
        when(mailServiceProvider.getIfAvailable()).thenReturn(null);

        StepVerifier.create(sender().send(PasswordRecoveryEntity.CHANNEL_EMAIL, "a@b.com", CODE))
                .expectErrorMatches(RecoveryChallengeSenderImplTest::isSmsServiceError)
                .verify();
    }

    @Test
    @DisplayName("未知通道：参数非法")
    void unsupportedChannel() {
        StepVerifier.create(sender().send("WECHAT", "wx-1", CODE))
                .expectErrorMatches(error -> error instanceof BusinessException businessException
                        && businessException.getCode().equals(ResultCode.PARAM_INVALID.getCode()))
                .verify();
    }

    private static Map<String, Object> response(String code) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", code);
        return response;
    }

    private static boolean isSmsServiceError(Throwable error) {
        return error instanceof BusinessException businessException
                && businessException.getCode().equals(ResultCode.SMS_SERVICE_ERROR.getCode());
    }
}
