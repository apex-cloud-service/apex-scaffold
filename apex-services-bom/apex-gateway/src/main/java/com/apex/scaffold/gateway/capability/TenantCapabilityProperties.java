package com.apex.scaffold.gateway.capability;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 开放接口租户能力门禁配置（D4，{@code apex.gateway.capability.*}）。
 *
 * <p>网关经 system 内部端点拉取租户能力订阅快照并做短 TTL 缓存，
 * 供 {@code /open-api/**} 能力门禁判定使用。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "apex.gateway.capability")
public class TenantCapabilityProperties {

    /** 是否启用能力订阅门禁；关闭时 {@code /open-api/**} 仅做 scope 断言 */
    private boolean enabled = true;

    /** system 服务内部端点基地址（集群内直连，不经网关） */
    private String baseUrl = "http://127.0.0.1:8082";

    /** 租户能力查询路径（{@code {code}} 为租户编码占位符） */
    private String path = "/internal/v1/tenants/{code}/capabilities";

    /** 单次查询超时（毫秒） */
    private long timeoutMillis = 2000;

    /** 租户能力快照缓存 TTL（毫秒），须与套餐变更传播预算对齐 */
    private long cacheTtlMillis = 30000;
}
