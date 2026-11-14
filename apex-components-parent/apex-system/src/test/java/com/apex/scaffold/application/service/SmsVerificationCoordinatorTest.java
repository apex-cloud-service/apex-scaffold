package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.config.SmsSendLimitProperties;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.service.SmsCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 短信验证码发送协调器单元测试。
 *
 * @author Apex Team
 * @since 3.2.0
 */
@ExtendWith(MockitoExtension.class)
class SmsVerificationCoordinatorTest {

    /** 短信验证码服务。 */
    @Mock
    private SmsCodeService smsCodeService;

    /** Redis 响应式模板。 */
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    /** 被测协调器。 */
    private SmsVerificationCoordinator coordinator;

    /**
     * 初始化三次、五分钟的手机号限流配置。
     *
     */
    @BeforeEach
    void setUp() {
        SmsSendLimitProperties properties = new SmsSendLimitProperties();
        properties.setMaxAttempts(3);
        properties.setWindowSeconds(300L);
        coordinator = new SmsVerificationCoordinator(smsCodeService, redisTemplate, properties);
    }

    /**
     * 验证窗口内请求未超过阈值时调用真实短信服务。
     *
     */
    @Test
    @DisplayName("手机号未超限时发送验证码")
    @SuppressWarnings("unchecked")
    void sendsWhenPhoneIsWithinLimit() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), eq("300")))
                .thenReturn(Flux.just(3L));
        when(smsCodeService.sendAndStore("13800138000")).thenReturn(Mono.empty());

        StepVerifier.create(coordinator.send("13800138000")).verifyComplete();

        verify(smsCodeService).sendAndStore("13800138000");
    }

    /**
     * 验证窗口内请求超过阈值时拒绝调用短信供应商。
     *
     */
    @Test
    @DisplayName("手机号超限时返回统一限流错误")
    @SuppressWarnings("unchecked")
    void rejectsWhenPhoneExceedsLimit() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), eq("300")))
                .thenReturn(Flux.just(4L));

        StepVerifier.create(coordinator.send("13800138000"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.RATE_LIMIT.getCode());
                })
                .verify();

        verify(smsCodeService, never()).sendAndStore(any());
    }

    /**
     * 验证 Redis 不返回计数时按依赖不可用处理，避免绕过限流。
     *
     */
    @Test
    @DisplayName("Redis无计数时关闭式失败")
    @SuppressWarnings("unchecked")
    void failsClosedWhenRedisReturnsNoCount() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), eq("300")))
                .thenReturn(Flux.empty());

        StepVerifier.create(coordinator.send("13800138000"))
                .expectErrorMatches(
                        error -> error instanceof IllegalStateException && "短信限流存储不可用".equals(error.getMessage()))
                .verify();

        verify(smsCodeService, never()).sendAndStore(any());
    }
}
