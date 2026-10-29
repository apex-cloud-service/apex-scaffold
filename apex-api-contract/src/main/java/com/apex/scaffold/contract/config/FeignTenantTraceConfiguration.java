package com.apex.scaffold.contract.config;

import com.apex.scaffold.common.constant.SystemConstants;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.util.TraceIdUtil;
import feign.RequestInterceptor;
import lombok.extern.slf4j.Slf4j;

/**
 * Feign 请求租户与追踪上下文透传配置.
 *
 * <p><b>作用</b>：在每次 Feign 出站调用前，通过 {@link RequestInterceptor} 自动注入
 * 内部 API 互认所需的标识头：</p>
 * <ul>
 *   <li>{@code X-Tenant-Id}：从 {@link TenantContext} 读取当前线程已装配的租户 ID，
 *       保证跨服务调用的多租户隔离（服务侧根据该头重建租户上下文）。</li>
 *   <li>{@code X-Trace-Id}：从 {@link TraceIdUtil} 读取当前链路追踪 ID（复用
 *       {@link SystemConstants#TRACE_ID_HEADER} 常量），保证分布式调用日志、SkyWalking 链路可串联。</li>
 * </ul>
 *
 * <p><b>使用方式</b>：在 {@code @FeignClient} 注解的 {@code configuration} 属性中显式引入，
 * 见 {@code AuthFeignClient} / {@code SystemFeignClient} / {@code WorkflowFeignClient}。</p>
 *
 * <p><b>说明</b>：拦截器仅在读取到相应上下文时才注入，未装配租户（如纯系统级调用）或
 * 未生成追踪 ID 时不强制覆盖已有头，避免破坏调用方显式指定的值。</p>
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Slf4j
public class FeignTenantTraceConfiguration {

    /** 租户 ID 请求头名称（与网关 {@code UserContextRelayFilter} 注入的头一致） */
    static final String HEADER_TENANT_ID = "X-Tenant-Id";

    /**
     * 租户与追踪透传拦截器.
     *
     * <p>通过返回值注入 Spring 管理的 {@link RequestInterceptor} Bean，
     * 使所有启用本配置的 Feign 客户端在请求模板展开时自动织入上下文头。</p>
     *
     * @return Feign 请求拦截器
     */
    public RequestInterceptor tenantTraceInterceptor() {
        return template -> {
            // 透传当前线程租户 ID（若已装配）；避免覆盖调用方显式设置的头
            TenantContext.currentTenantId()
                    .filter(value -> !template.headers().containsKey(HEADER_TENANT_ID))
                    .ifPresent(value -> template.header(HEADER_TENANT_ID, value));
            // 透传当前链路追踪 ID（若未装配则生成）；避免覆盖既有追踪 ID 头
            String traceId = TraceIdUtil.getTraceId();
            if (!template.headers().containsKey(SystemConstants.TRACE_ID_HEADER)) {
                template.header(SystemConstants.TRACE_ID_HEADER, traceId);
            } else {
                log.debug("Feign 请求已携带 X-Trace-Id，跳过透传注入");
            }
        };
    }
}
