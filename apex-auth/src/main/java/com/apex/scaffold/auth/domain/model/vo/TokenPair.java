package com.apex.scaffold.auth.domain.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token对值对象
 *
 * <p>封装访问令牌和刷新令牌，作为认证成功后的返回结果。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenPair {

    /**
     * 访问令牌
     */
    private String accessToken;

    /**
     * 刷新令牌
     */
    private String refreshToken;

    /**
     * 刷新令牌JTI
     *
     * <p>Refresh Token的JWT ID（jti声明）。
     * 用于审计日志记录和Token追踪，便于排查问题和安全审计。
     */
    private String refreshTokenJti;

    /**
     * 访问令牌过期时间（秒）
     */
    private Long expiresIn;

    /**
     * Token类型
     */
    private String tokenType;
}
