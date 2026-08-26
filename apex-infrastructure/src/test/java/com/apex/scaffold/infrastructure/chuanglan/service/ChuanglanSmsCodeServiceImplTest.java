package com.apex.scaffold.infrastructure.chuanglan.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.domain.gateway.ChuanglanGateway;
import com.apex.scaffold.domain.gateway.ChuanglanGateway.SmsSendResult;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 创蓝短信验证码服务实现单元测试类
 *
 * <p>测试 {@link ChuanglanSmsCodeServiceImpl} 的验证码发送和验证逻辑。
 *
 * <h2>测试覆盖范围</h2>
 * <ul>
 *   <li>{@link ChuanglanSmsCodeServiceImpl#sendAndStore(String)} - 生成验证码、存储到Redis、发送短信</li>
 *   <li>{@link ChuanglanSmsCodeServiceImpl#validate(String, String)} - 验证码校验</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChuanglanSmsCodeServiceImpl 单元测试")
class ChuanglanSmsCodeServiceImplTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ChuanglanGateway chuanglanGateway;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @InjectMocks
    private ChuanglanSmsCodeServiceImpl smsCodeService;

    private static final Duration CODE_TTL = Duration.ofMinutes(5);

    /**
     * 测试前置：配置 Redis 操作 Mock。
     */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));
        lenient().when(valueOperations.get(anyString())).thenReturn(Mono.empty());
        lenient()
                .when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));
    }

    // ==================== sendAndStore 测试 ====================

    @Nested
    @DisplayName("sendAndStore - 生成并发送验证码")
    class SendAndStoreTests {

        /**
         * 测试成功发送验证码：验证码被存储到 Redis（5分钟TTL），短信发送成功。
         */
        @Test
        @DisplayName("成功发送验证码：存储到Redis并发送短信")
        void sendAndStore_Success() {
            when(valueOperations.set(anyString(), anyString(), eq(CODE_TTL))).thenReturn(Mono.just(true));

            SmsSendResult sendResult = new SmsSendResult("msg-id-001", true);
            when(chuanglanGateway.sendSms(eq("13800138000"), anyString())).thenReturn(Mono.just(sendResult));

            StepVerifier.create(smsCodeService.sendAndStore("13800138000")).verifyComplete();

            verify(valueOperations).set(startsWith("cl:sms:code:13800138000"), anyString(), eq(CODE_TTL));
            verify(chuanglanGateway).sendSms(eq("13800138000"), anyString());
        }

        /**
         * 测试 phone 为 null 时跳过发送。
         */
        @Test
        @DisplayName("phone为null应跳过发送")
        void sendAndStore_NullPhone_Skips() {
            StepVerifier.create(smsCodeService.sendAndStore(null)).verifyComplete();

            verify(valueOperations, never()).set(anyString(), anyString(), any());
            verify(chuanglanGateway, never()).sendSms(anyString(), anyString());
        }

        /**
         * 测试 phone 为空字符串时跳过发送。
         */
        @Test
        @DisplayName("phone为空字符串应跳过发送")
        void sendAndStore_BlankPhone_Skips() {
            StepVerifier.create(smsCodeService.sendAndStore("  ")).verifyComplete();

            verify(valueOperations, never()).set(anyString(), anyString(), any());
            verify(chuanglanGateway, never()).sendSms(anyString(), anyString());
        }

        /**
         * 测试短信发送失败时，清除 Redis 中已缓存的验证码。
         */
        @Test
        @DisplayName("短信发送失败应清除Redis缓存")
        void sendAndStore_SendFailed_DeletesCache() {
            when(valueOperations.set(anyString(), anyString(), eq(CODE_TTL))).thenReturn(Mono.just(true));

            SmsSendResult failResult = new SmsSendResult(null, false);
            failResult.setErrorCode("E1003");
            failResult.setErrorMessage("余额不足");
            when(chuanglanGateway.sendSms(eq("13800138000"), anyString())).thenReturn(Mono.just(failResult));

            when(valueOperations.delete(anyString())).thenReturn(Mono.just(true));

            StepVerifier.create(smsCodeService.sendAndStore("13800138000"))
                    .expectError(IllegalStateException.class)
                    .verify();

            verify(valueOperations).set(startsWith("cl:sms:code:13800138000"), anyString(), eq(CODE_TTL));
            verify(chuanglanGateway).sendSms(eq("13800138000"), anyString());
            verify(valueOperations).delete(startsWith("cl:sms:code:13800138000"));
        }
    }

    // ==================== validate 测试 ====================

    @Nested
    @DisplayName("validate - 验证码校验")
    class ValidateTests {

        /**
         * 测试验证码正确：从 Redis 获取验证码比对成功，验证成功后删除缓存。
         */
        @Test
        @DisplayName("验证码正确返回true")
        void validate_Success() {
            when(redisTemplate.execute(
                            any(RedisScript.class), eq(java.util.List.of("cl:sms:code:13800138000")), eq("123456")))
                    .thenReturn(Flux.just(1L));

            StepVerifier.create(smsCodeService.validate("13800138000", "123456"))
                    .assertNext(result -> assertThat(result).isTrue())
                    .verifyComplete();

            verify(redisTemplate)
                    .execute(any(RedisScript.class), eq(java.util.List.of("cl:sms:code:13800138000")), eq("123456"));
        }

        /**
         * 测试验证码错误：从 Redis 获取的验证码与输入不一致。
         */
        @Test
        @DisplayName("验证码错误返回false")
        void validate_WrongCode() {
            when(redisTemplate.execute(
                            any(RedisScript.class), eq(java.util.List.of("cl:sms:code:13800138000")), eq("123456")))
                    .thenReturn(Flux.just(-1L));

            StepVerifier.create(smsCodeService.validate("13800138000", "123456"))
                    .assertNext(result -> assertThat(result).isFalse())
                    .verifyComplete();

            verify(redisTemplate)
                    .execute(any(RedisScript.class), eq(java.util.List.of("cl:sms:code:13800138000")), eq("123456"));
        }

        /**
         * 测试验证码不存在或已过期：Redis 返回空，defaultIfEmpty 返回 false。
         */
        @Test
        @DisplayName("验证码不存在或已过期返回false")
        void validate_ExpiredOrNotFound() {
            when(redisTemplate.execute(
                            any(RedisScript.class), eq(java.util.List.of("cl:sms:code:13800138000")), eq("123456")))
                    .thenReturn(Flux.just(0L));

            StepVerifier.create(smsCodeService.validate("13800138000", "123456"))
                    .assertNext(result -> assertThat(result).isFalse())
                    .verifyComplete();

            verify(redisTemplate)
                    .execute(any(RedisScript.class), eq(java.util.List.of("cl:sms:code:13800138000")), eq("123456"));
        }

        /**
         * 测试 phone 为 null 时返回 false。
         */
        @Test
        @DisplayName("phone为null返回false")
        void validate_NullPhone_ReturnsFalse() {
            StepVerifier.create(smsCodeService.validate(null, "123456"))
                    .assertNext(result -> assertThat(result).isFalse())
                    .verifyComplete();

            verifyNoInteractions(redisTemplate);
        }

        /**
         * 测试 code 为 null 时返回 false。
         */
        @Test
        @DisplayName("code为null返回false")
        void validate_NullCode_ReturnsFalse() {
            StepVerifier.create(smsCodeService.validate("13800138000", null))
                    .assertNext(result -> assertThat(result).isFalse())
                    .verifyComplete();

            verifyNoInteractions(redisTemplate);
        }

        /**
         * 测试 code 为空字符串时返回 false。
         */
        @Test
        @DisplayName("code为空字符串返回false")
        void validate_BlankCode_ReturnsFalse() {
            StepVerifier.create(smsCodeService.validate("13800138000", "  "))
                    .assertNext(result -> assertThat(result).isFalse())
                    .verifyComplete();

            verifyNoInteractions(redisTemplate);
        }
    }
}
