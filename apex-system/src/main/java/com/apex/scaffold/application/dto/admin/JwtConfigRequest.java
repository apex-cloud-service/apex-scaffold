package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JWT 配置请求对象
 *
 * <p>用于更新 JWT 令牌相关的配置参数，包括密钥、过期时间、刷新策略等。
 * 所有参数均包含 JSR-303 校验注解以确保数据合法性。
 *
 * @author Apex Scaffold Generator
 * @since 2026-09-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "JWT 配置请求对象")
public class JwtConfigRequest {

    @NotBlank(message = "JWT 密钥不能为空")
    @Schema(description = "JWT 签名密钥", required = true, example = "your-secret-key-at-least-256-bits")
    private String secret;

    @NotNull(message = "访问令牌过期时间不能为空")
    @Min(value = 60, message = "访问令牌过期时间不能小于 60 秒")
    @Schema(description = "访问令牌过期时间（秒）", required = true, example = "7200")
    private Integer accessTokenExpiration;

    @NotNull(message = "刷新令牌过期时间不能为空")
    @Min(value = 3600, message = "刷新令牌过期时间不能小于 3600 秒")
    @Schema(description = "刷新令牌过期时间（秒）", required = true, example = "604800")
    private Integer refreshTokenExpiration;

    @NotNull(message = "令牌刷新时间窗口不能为空")
    @Min(value = 0, message = "令牌刷新时间窗口不能为负数")
    @Schema(description = "令牌刷新时间窗口（秒），在此时间内允许使用旧令牌刷新", required = true, example = "300")
    private Integer refreshWindowSeconds;

    @NotNull(message = "是否启用刷新令牌轮换不能为空")
    @Schema(description = "是否启用刷新令牌轮换（每次刷新生成新的刷新令牌）", required = true, example = "true")
    private Boolean enableRefreshTokenRotation;

    @NotNull(message = "是否验证令牌签发者不能为空")
    @Schema(description = "是否验证令牌签发者", required = true, example = "true")
    private Boolean validateIssuer;

    @Schema(description = "令牌签发者标识", example = "apex-scaffold")
    private String issuer;

    @NotNull(message = "是否验证令牌接收者不能为空")
    @Schema(description = "是否验证令牌接收者", required = true, example = "false")
    private Boolean validateAudience;

    @Schema(description = "令牌接收者标识", example = "apex-client")
    private String audience;
}
