package com.apex.scaffold.contract.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.constant.SystemConstants;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.util.TraceIdUtil;
import feign.Request;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link FeignTenantTraceConfiguration} 租户与追踪上下文头注入测试.
 *
 * <p>校验内部 API 出站调用前，拦截器能正确从 {@link TenantContext} / {@link TraceIdUtil}
 * 装配 {@code X-Tenant-Id} / {@code X-Trace-Id} 头：有值透传、无值不注入、显式头不覆盖。
 * 头注入行为是跨服务多租户隔离与链路追踪的关键保障之一。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("FeignTenantTraceConfiguration 头注入测试")
class FeignTenantTraceConfigurationTest {

    private final RequestInterceptor interceptor = new FeignTenantTraceConfiguration().tenantTraceInterceptor();

    @Test
    void interceptorUsesSingleMethodLambda() {
        assertThat(interceptor.getClass().isAnonymousClass()).isFalse();
    }

    @AfterEach
    void cleanThreadLocal() {
        // 清理测试线程上下文，避免租户 / 追踪 ID 泄漏到其它用例
        TenantContext.clear();
        TraceIdUtil.clear();
    }

    /** 构造一个空请求模板，模拟一次出站 Feign 调用 */
    private static RequestTemplate newTemplate() {
        return new RequestTemplate().method(Request.HttpMethod.GET).uri("/internal/v1/users/10086");
    }

    @Nested
    @DisplayName("租户 ID 透传")
    class TenantHeaderTest {

        @Test
        @DisplayName("已装配租户时注入 X-Tenant-Id")
        void injectTenantId() {
            String previous = TenantContext.setTenantId("tenant-9527");
            try {
                RequestTemplate template = newTemplate();
                interceptor.apply(template);
                assertThat(template.headers()).containsKey("X-Tenant-Id");
                assertThat(template.headers().get("X-Tenant-Id")).containsExactly("tenant-9527");
            } finally {
                TenantContext.restoreTenantId(previous);
            }
        }

        @Test
        @DisplayName("未装配租户时不注入 X-Tenant-Id")
        void notInjectWhenNoTenant() {
            RequestTemplate template = newTemplate();
            interceptor.apply(template);
            assertThat(template.headers()).doesNotContainKey("X-Tenant-Id");
        }

        @Test
        @DisplayName("已存在显式 X-Tenant-Id 时不覆盖")
        void notOverrideExistingTenantHeader() {
            String previous = TenantContext.setTenantId("tenant-context");
            try {
                RequestTemplate template = newTemplate();
                template.header("X-Tenant-Id", "tenant-explicit");
                interceptor.apply(template);
                assertThat(template.headers().get("X-Tenant-Id")).containsExactly("tenant-explicit");
            } finally {
                TenantContext.restoreTenantId(previous);
            }
        }
    }

    @Nested
    @DisplayName("链路追踪 ID 透传")
    class TraceHeaderTest {

        @Test
        @DisplayName("自动生成并注入 X-Trace-Id")
        void injectAutoTraceId() {
            RequestTemplate template = newTemplate();
            interceptor.apply(template);
            String injected = headerValue(template, SystemConstants.TRACE_ID_HEADER);
            assertThat(injected).isNotBlank().hasSize(32);
        }

        @Test
        @DisplayName("已存在显式 X-Trace-Id 时不覆盖")
        void notOverrideExistingTraceHeader() {
            String explicit = "abcdef0123456789abcdef0123456789";
            RequestTemplate template = newTemplate();
            template.header(SystemConstants.TRACE_ID_HEADER, explicit);
            interceptor.apply(template);
            assertThat(headerValue(template, SystemConstants.TRACE_ID_HEADER)).isEqualTo(explicit);
        }
    }

    /** 单值 header 的便捷读取 */
    private static String headerValue(RequestTemplate template, String name) {
        var values = template.headers().get(name);
        assertThat(values).as("header %s 应存在", name).isNotNull();
        return values.iterator().next();
    }
}
