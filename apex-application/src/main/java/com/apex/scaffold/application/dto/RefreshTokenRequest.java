package com.apex.scaffold.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Token 刷新请求 DTO。
 *
 * <p>将 RefreshToken 从 URL 查询参数迁移至请求 Body（JSON），
 * 避免 Token 值被服务器访问日志、代理中间件和 CDN 日志记录。</p>
 *
 * @author Apex Team
 * @since 1.0.0
 */
@Data
public class RefreshTokenRequest {

    /** RefreshToken 字符串（必填） */
    @NotBlank(message = "{validation.refresh_token_not_blank}")
    private String refreshToken;

    /** 设备指纹标识（可选，用于校验设备一致性） */
    private String deviceId;
}
