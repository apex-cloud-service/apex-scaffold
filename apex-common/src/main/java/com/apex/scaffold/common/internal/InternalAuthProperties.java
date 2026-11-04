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

    /**
     * 豁免签名的内部路径前缀。
     *
     * <p>默认豁免 EMQX HTTP 认证/ACL 钩子：broker 无法持有服务签名，这两端点由设备
     * 凭据与 topic ACL 自行约束。新增豁免必须说明外部调用方及其自身认证机制。
     */
    private java.util.List<String> exemptPaths =
            java.util.List.of("/internal/v1/iot/mqtt/auth", "/internal/v1/iot/mqtt/acl");
}
