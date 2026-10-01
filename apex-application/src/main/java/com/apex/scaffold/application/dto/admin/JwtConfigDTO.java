package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JWT 配置响应对象
 *
 * <p>用于返回 JWT 令牌配置信息，包括密钥、过期时间、刷新策略等。
 * 包含审计字段以追踪配置的创建和更新历史。
 *
 * @author Apex Scaffold Generator
 * @since 2026-09-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "JWT 配置响应对象")
public class JwtConfigDTO {

    @Schema(description = "配置 ID", example = "1")
    private Long id;

    @Schema(description = "租户 ID", example = "1")
    private Long tenantId;

    @Schema(description = "JWT 签名密钥（脱敏显示）", example = "your-secret-***")
    private String secret;

    @Schema(description = "签名算法", example = "HS256")
    private String algorithm;

    @Schema(description = "访问令牌过期时间（秒）", example = "7200")
    private Integer accessTokenExpiration;

    @Schema(description = "刷新令牌过期时间（秒）", example = "604800")
    private Integer refreshTokenExpiration;

    @Schema(description = "令牌刷新时间窗口（秒）", example = "300")
    private Integer refreshWindowSeconds;

    @Schema(description = "是否启用刷新令牌轮换", example = "true")
    private Boolean enableRefreshTokenRotation;

    @Schema(description = "是否验证令牌签发者", example = "true")
    private Boolean validateIssuer;

    @Schema(description = "令牌签发者标识", example = "apex-scaffold")
    private String issuer;

    @Schema(description = "是否验证令牌接收者", example = "false")
    private Boolean validateAudience;

    @Schema(description = "令牌接收者标识", example = "apex-client")
    private String audience;

    @Schema(description = "创建时间", example = "2026-09-19T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间", example = "2026-09-19T10:00:00")
    private LocalDateTime updatedAt;

    @Schema(description = "创建人 ID", example = "1")
    private Long createdBy;

    @Schema(description = "更新人 ID", example = "1")
    private Long updatedBy;
}
