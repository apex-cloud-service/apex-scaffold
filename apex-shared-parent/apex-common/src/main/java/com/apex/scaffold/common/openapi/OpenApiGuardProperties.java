package com.apex.scaffold.common.openapi;

import java.util.LinkedHashMap;
import java.util.Map;
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

    /**
     * 是否启用服务侧二次校验；默认开启，与网关能力门禁默认口径一致。
     *
     * <p>确需关闭的实例必须显式配置 {@code apex.open-api.guard.enabled=false}
     * 并在配置注释中写明原因与补偿措施；关闭后开放流量仅由网关单侧校验。
     */
    private boolean enabled = true;

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

    /** 开放接口路径前缀（进入该前缀的请求一律按开放流量校验，不依赖标记头） */
    private String pathPrefix = "/open-api/";

    /**
     * 开放路径 → 能力编码映射（路径前缀，长前缀优先）。
     *
     * <p>服务侧据此从实际请求路径独立推导能力，避免删除或篡改 marker 头绕过校验。
     * 未命中映射的开放路径按拒绝处理（fail-closed）。
     */
    private Map<String, String> pathCapabilities = new LinkedHashMap<>(Map.of(
            "/open-api/file/", "file-storage",
            "/open-api/workflow/", "workflow",
            "/open-api/users", "user-org",
            "/open-api/user/", "user-org",
            "/open-api/messages", "office-message",
            "/open-api/messages/", "office-message"));
}
