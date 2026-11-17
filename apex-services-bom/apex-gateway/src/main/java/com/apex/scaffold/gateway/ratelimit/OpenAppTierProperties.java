package com.apex.scaffold.gateway.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 开放应用限流档查询配置（D2 按应用分档限流，{@code apex.gateway.open-app-tier.*}）。
 *
 * <p>网关经 system 内部端点 {@code GET /internal/v1/open-apps/{clientId}/rate-limit-tier}
 * 拉取开放应用注册时声明的限流档编码并做短 TTL 缓存；档位 → 阈值的数值映射见
 * {@code GatewayRateLimitProperties#getOpenApiTiers()}。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "apex.gateway.open-app-tier")
public class OpenAppTierProperties {

    /** 是否启用按应用分档；关闭时所有开放应用按既有默认阈值判定 */
    private boolean enabled = true;

    /** system 服务内部端点基地址（集群内直连，不经网关） */
    private String baseUrl = "http://127.0.0.1:8082";

    /** 限流档查询路径（{@code {clientId}} 为客户端 ID 占位符） */
    private String path = "/internal/v1/open-apps/{clientId}/rate-limit-tier";

    /** 单次查询超时（毫秒） */
    private long timeoutMillis = 2000;

    /** 限流档缓存 TTL（毫秒），须与应用档位变更传播预算对齐 */
    private long cacheTtlMillis = 60000;
}
