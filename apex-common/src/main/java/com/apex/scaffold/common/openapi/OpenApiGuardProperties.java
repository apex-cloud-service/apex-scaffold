package com.apex.scaffold.common.openapi;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 服务侧开放接口能力二次校验配置（方案 D4，{@code apex.open-api.guard.*}）。
 *
 * <p>网关在 {@code /open-api/**} 放行时注入可信标记头 {@code X-Open-Api-Capability}
 * （外部伪造值由网关剥离）；业务服务据此做能力订阅二次校验，与网关共用同一
 * {@link com.apex.scaffold.common.capability.CapabilityGate} 判定实现，避免两端口径漂移。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "apex.open-api.guard")
public class OpenApiGuardProperties {

    /** 是否启用服务侧二次校验；默认关闭，暴露开放能力的业务服务显式开启 */
    private boolean enabled = false;

    /** system 服务内部端点基地址（集群内直连，不经网关） */
    private String baseUrl = "http://127.0.0.1:8082";

    /** 租户能力查询路径（{@code {code}} 为租户编码占位符） */
    private String path = "/internal/v1/tenants/{code}/capabilities";

    /** 单次查询超时（毫秒） */
    private long timeoutMillis = 2000;

    /** 租户能力快照缓存 TTL（毫秒） */
    private long cacheTtlMillis = 30000;

    /** 网关注入的开放能力标记头（请求携带该头即按开放流量二次校验） */
    private String markerHeader = "X-Open-Api-Capability";

    /** 可信租户头（网关上下文中继注入，服务侧据此判定租户订阅） */
    private String tenantHeader = "X-Tenant-Id";
}
