package com.apex.scaffold.gateway.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关令牌撤销流配置属性（{@code apex.gateway.revocation.*}，统一认证收口 A3）。
 *
 * <p>网关周期拉取认证中心撤销增量（{@code GET /internal/v1/tokens/revocations}），
 * 在本地 Caffeine 缓存已撤销 {@code jti}；验签通过的令牌命中缓存即 401。
 * {@code enabled} 与 {@code url} 同时满足时拉取生效。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "apex.gateway.revocation")
public class RevocationProperties {

    /** 是否启用网关撤销缓存（启用且 url 非空时拉取生效） */
    private boolean enabled = false;

    /** 撤销流地址（认证中心内部端点完整 URL） */
    private String url = "";

    /** 拉取间隔（秒，下限 5）；撤销生效时延上限 ≈ 间隔 + 1 次请求时长 */
    private long pullIntervalSeconds = 15;

    /** 启动游标回看窗口（秒）：重启后从 now−lookback 起补拉，默认覆盖 2 小时访问令牌全生命周期 */
    private long lookbackSeconds = 7200;

    /** 单批拉取上限（透传给服务端，服务端收敛到 [1, 1000]） */
    private int batchLimit = 500;

    /** 拉取超时（毫秒） */
    private long timeoutMillis = 5000;
}
