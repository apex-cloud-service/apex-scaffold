package com.apex.scaffold.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.domain.service.SmsCodeService;
import com.apex.scaffold.infrastructure.cache.service.SmsCodeServiceImpl;
import com.apex.scaffold.infrastructure.chuanglan.service.ChuanglanSmsCodeServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;

/**
 * 验证短信验证码基础设施实现遵循领域接口和 Spring Bean 命名契约。
 *
 * @author weiqiang.ling
 */
@DisplayName("SmsCodeService 基础设施实现契约测试")
class SmsCodeServiceImplementationsContractTest {

    /**
     * 执行 {@code bothImplementationsShouldImplementSmsCodeService} 操作。
     */
    @Test
    @DisplayName("两个实现类都应实现 SmsCodeService 接口")
    void bothImplementationsShouldImplementSmsCodeService() {
        assertThat(SmsCodeService.class).isAssignableFrom(SmsCodeServiceImpl.class);
        assertThat(SmsCodeService.class).isAssignableFrom(ChuanglanSmsCodeServiceImpl.class);
    }

    /**
     * 执行 {@code implementationsShouldHaveDifferentServiceNames} 操作。
     */
    @Test
    @DisplayName("两个实现类应使用不同且非空的 Spring Bean 名称")
    void implementationsShouldHaveDifferentServiceNames() {
        Service redisService = SmsCodeServiceImpl.class.getAnnotation(Service.class);
        Service chuanglanService = ChuanglanSmsCodeServiceImpl.class.getAnnotation(Service.class);

        assertThat(redisService).isNotNull();
        assertThat(chuanglanService).isNotNull();
        assertThat(redisService.value()).isNotBlank();
        assertThat(chuanglanService.value()).isNotBlank();
        assertThat(redisService.value()).isNotEqualTo(chuanglanService.value());
    }
}
