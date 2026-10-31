package com.apex.scaffold.auth.domain.model;

/**
 * 令牌撤销增量记录：供撤销流（统一认证收口 A3）向网关等消费方暴露。
 *
 * <p>平台级数据（跨租户）：网关按 {@code jti} 维度缓存，不携带租户语义；
 * 撤销时间用于游标推进。
 *
 * @param jti              JWT 唯一标识
 * @param revokedAtMillis  撤销时间（epoch 毫秒）
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public record TokenRevocation(String jti, long revokedAtMillis) {}
