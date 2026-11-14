package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Token刷新请求DTO，通过请求体提交刷新令牌，避免令牌出现在URL中被日志记录")
public class RefreshTokenRequest {

    /** RefreshToken 字符串（必填） */
    @Schema(
            description = "刷新令牌字符串，用于换取新的访问令牌",
            example = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMDAxIn0.abcdef123456",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{validation.refresh_token_not_blank}")
    private String refreshToken;

    /** 设备指纹标识（可选，用于校验设备一致性） */
    @Schema(description = "设备指纹标识，用于校验设备一致性", example = "d8f2a1b3c4e5f6a7")
    private String deviceId;
}
