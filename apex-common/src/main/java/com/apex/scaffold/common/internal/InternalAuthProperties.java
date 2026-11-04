package com.apex.scaffold.common.internal;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 内部服务调用签名配置（{@code apex.internal-auth.*}）。
 *
 * <p>默认开启；未配置密钥时内部接口拒绝所有调用（fail-closed）。仅本地单测或纯开发
 * 环境可显式关闭，生产必须配置稳定密钥并通过部署编排注入。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "apex.internal-auth")
public class InternalAuthProperties {

    /** 是否启用内部接口签名校验；默认开启。 */
    private boolean enabled = true;

    /** 共享签名密钥（至少 32 字节）；空白表示未配置。 */
    private String secret = "";

    /** 本服务名（写入 {@code X-Internal-Service}）。 */
    private String serviceName = "apex-service";

    /** 允许的时钟偏差（秒），默认 60。 */
    private long allowedSkewSeconds = 60;

    /** 调用方服务名到可访问的内部路径模式；未登记调用方默认拒绝。 */
    private java.util.Map<String, java.util.List<String>> callers = new java.util.LinkedHashMap<>(java.util.Map.of(
            "apex-gateway",
                    java.util.List.of(
                            "/internal/v1/tenants/**", "/internal/v1/open-apps/**", "/internal/v1/tokens/revocations"),
            "apex-system-service",
                    java.util.List.of(
                            "/internal/v1/open-apps/**",
                            "/internal/v1/file/stats",
                            "/internal/v1/workflow/stats",
                            "/internal/v1/iot/devices/stats",
                            "/internal/v1/office/messages/stats",
                            "/internal/v1/users/**",
                            "/internal/v1/tokens/**"),
            "apex-auth-service", java.util.List.of("/internal/v1/tenants/**", "/internal/v1/open-app-subjects/**"),
            "apex-file-service", java.util.List.of("/internal/v1/tenants/**", "/internal/v1/open-app-subjects/**"),
            "apex-workflow-service", java.util.List.of("/internal/v1/tenants/**", "/internal/v1/open-app-subjects/**"),
            "apex-office-service",
                    java.util.List.of(
                            "/internal/v1/tenants/**", "/internal/v1/org/**", "/internal/v1/open-app-subjects/**"),
            "apex-integration-service", java.util.List.of("/internal/v1/notify", "/internal/v1/tenants/**")));

    /** 仅撤销流与平台应用档位查询允许无租户签名；其他路径必须绑定租户。 */
    private java.util.List<String> unscopedPaths =
            java.util.List.of("/internal/v1/tokens/revocations", "/internal/v1/open-apps/*/rate-limit-tier");

    /**
     * 豁免签名的内部路径前缀。
     *
     * <p>默认豁免 EMQX HTTP 认证/ACL 钩子：broker 无法持有服务签名，这两端点由设备
     * 凭据与 topic ACL 自行约束。新增豁免必须说明外部调用方及其自身认证机制。
     */
    private java.util.List<String> exemptPaths =
            java.util.List.of("/internal/v1/iot/mqtt/auth", "/internal/v1/iot/mqtt/acl");
}
