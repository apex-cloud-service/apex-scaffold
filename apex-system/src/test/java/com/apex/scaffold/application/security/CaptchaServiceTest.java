package com.apex.scaffold.application.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 图形验证码应用服务单元测试。
 *
 * <p>覆盖验证码生成、一次性校验（正确/错误/不存在/禁用）等核心逻辑，
 * 验证码答案通过 mock Redis 模板验证存取行为。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class CaptchaServiceTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    /** 验证码配置属性 */
    private CaptchaProperties properties;

    /** 验证码服务 */
    private CaptchaService captchaService;

    /**
     * 初始化测试数据。
     */
    @BeforeEach
    void setUp() {
        properties = new CaptchaProperties();
        properties.setEnabled(true);
        properties.setLength(4);
        properties.setTtl(Duration.ofMinutes(2));
        captchaService = new CaptchaService(properties, redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    /**
     * 测试生成验证码成功。
     */
    @Test
    @DisplayName("generate - 成功生成验证码并写入缓存")
    void generate_Success() {
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        captchaService
                .generate()
                .as(StepVerifier::create)
                .assertNext(vo -> {
                    assertThat(vo.getCaptchaId()).isNotBlank();
                    assertThat(vo.getImage()).isNotBlank();
                    // Base64 图片应可解码为 PNG 头
                    byte[] decoded = java.util.Base64.getDecoder().decode(vo.getImage());
                    assertThat(decoded.length).isGreaterThan(100);
                })
                .verifyComplete();
    }

    /**
     * 测试验证码功能禁用时抛出异常。
     */
    @Test
    @DisplayName("generate - 验证码功能禁用时抛出异常")
    void generate_DisabledFails() {
        properties.setEnabled(false);

        captchaService
                .generate()
                .as(StepVerifier::create)
                .expectError(BusinessException.class)
                .verify();
    }

    /**
     * 测试校验正确的验证码。
     */
    @Test
    @DisplayName("verify - 正确验证码校验通过且一次性消费")
    void verify_CorrectCode() {
        when(valueOps.get("apex:captcha:abc123")).thenReturn(Mono.just("ABCD"));
        when(valueOps.delete("apex:captcha:abc123")).thenReturn(Mono.just(true));

        captchaService
                .verify("abc123", "abcd")
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();
    }

    /**
     * 测试校验错误的验证码。
     */
    @Test
    @DisplayName("verify - 错误验证码校验失败")
    void verify_WrongCode() {
        when(valueOps.get("apex:captcha:abc123")).thenReturn(Mono.just("ABCD"));
        when(valueOps.delete("apex:captcha:abc123")).thenReturn(Mono.just(true));

        captchaService
                .verify("abc123", "XXXX")
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }

    /**
     * 测试校验不存在的验证码。
     */
    @Test
    @DisplayName("verify - 验证码不存在或已过期时校验失败")
    void verify_NotFound() {
        when(valueOps.get("apex:captcha:missing")).thenReturn(Mono.empty());
        when(valueOps.delete("apex:captcha:missing")).thenReturn(Mono.just(true));

        captchaService
                .verify("missing", "ABCD")
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }

    /**
     * 测试参数缺失时直接返回失败。
     */
    @Test
    @DisplayName("verify - 参数为空时直接返回失败")
    void verify_BlankParams() {
        captchaService
                .verify(null, "ABCD")
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();

        captchaService
                .verify("abc", null)
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }
}
