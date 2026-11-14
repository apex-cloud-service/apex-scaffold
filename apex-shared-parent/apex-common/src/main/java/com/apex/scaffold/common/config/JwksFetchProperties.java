package com.apex.scaffold.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 共享 JWKS 拉取配置属性（{@code apex.jwks.*}）。
 *
 * <p>与 {@code config/nacos/apex-common.yaml} 预留的公共约定对齐：网关与各下游服务
 * 共享同一组键名，公钥可安全下发（无私钥）。{@code remote-url} 为空时拉取关闭，
 * 验签方回退到静态配置公钥兜底。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "apex.jwks")
public class JwksFetchProperties {

    /** JWKS 远程拉取地址（完整 URL 或主机基址）；空 = 拉取关闭 */
    private String remoteUrl = "";

    /** JWKS 端点路径；remote-url 仅为基址（无路径）时追加该路径 */
    private String endpoint = "/.well-known/jwks.json";

    /** 公钥缓存刷新间隔（秒）；密钥轮换后最迟在此时间内生效 */
    private long refreshIntervalSeconds = 300;

    /** 单次拉取超时（毫秒） */
    private long timeoutMillis = 5000;
}
