package com.apex.scaffold.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 刷新请求 DTO。
 *
 * <p>将 RefreshToken 从 URL 查询参数迁移至请求 Body（JSON），避免 Token 值被服务器访问日志、代理中间件和 CDN 日志记录。</p>
 *
 * <p>主要字段与校验规则：</p>
 * <ul>
 *   <li>{@code refreshToken} - 刷新令牌字符串，必填（{@code @NotBlank}）</li>
 *   <li>{@code deviceId} - 设备指纹标识，可选，用于校验设备一致性</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**n * RefreshTokenRequestn *n * <p>TODO: 补充类的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class RefreshTokenRequest {

    /** RefreshToken 字符串（必填） */
    @NotBlank(message = "{validation.refresh_token_not_blank}")
    private String refreshToken;

    /** 设备指纹标识（可选，用于校验设备一致性） */
    private String deviceId;
}
