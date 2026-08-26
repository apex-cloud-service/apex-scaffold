package com.apex.scaffold.infrastructure.cache.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.domain.service.SmsCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 默认短信验证码适配器单元测试。
 *
 * <p>验证默认认证入口不会在本地生成或打印验证码，而是完整委托给真实短信供应商实现。</p>
 *
 * @author Apex Team
 * @since 3.2.0
 */
@ExtendWith(MockitoExtension.class)
class SmsCodeServiceImplTest {

    /** 测试手机号。 */
    private static final String TEST_PHONE = "13800138000";

    /** 真实短信服务替身。 */
    @Mock
    private SmsCodeService delegate;

    /** 被测适配器。 */
    private SmsCodeServiceImpl smsCodeService;

    /**
     * 创建被测对象。
     *
     */
    @BeforeEach
    void setUp() {
        smsCodeService = new SmsCodeServiceImpl(delegate);
    }

    /**
     * 验证发送操作委托到真实供应商。
     *
     */
    @Test
    @DisplayName("默认入口应委托真实短信发送")
    void sendAndStoreDelegatesToProvider() {
        when(delegate.sendAndStore(TEST_PHONE)).thenReturn(Mono.empty());

        StepVerifier.create(smsCodeService.sendAndStore(TEST_PHONE)).verifyComplete();

        verify(delegate).sendAndStore(TEST_PHONE);
    }

    /**
     * 验证核销操作委托到原子校验实现。
     *
     */
    @Test
    @DisplayName("默认入口应委托验证码核销")
    void validateDelegatesToProvider() {
        when(delegate.validate(TEST_PHONE, "123456")).thenReturn(Mono.just(true));

        StepVerifier.create(smsCodeService.validate(TEST_PHONE, "123456"))
                .expectNext(true)
                .verifyComplete();

        verify(delegate).validate(TEST_PHONE, "123456");
    }
}
