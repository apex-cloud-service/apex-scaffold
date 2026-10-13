package com.apex.scaffold.auth.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录请求DTO
 *
 * <p>用户名密码登录的请求参数
 *
 * @author Apex Team
 * @since 2026-09-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录请求")
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 50, message = "用户名长度必须在2-50之间")
    @Schema(description = "用户名", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 128, message = "密码长度必须在6-128之间")
    @Schema(
            description = "密码",
            format = "password",
            accessMode = Schema.AccessMode.WRITE_ONLY,
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Schema(description = "租户匹配声明；省略时使用 default，必须与服务端配置的可信登录租户一致，不能用于切换租户", example = "default")
    private String tenantId;

    @Schema(description = "设备ID", example = "web-chrome-uuid")
    private String deviceId;

    /**
     * 客户端类型（可选，多端统一账户改造新增）。
     *
     * <p>PC 管理端固定上报 {@code PC_ADMIN}；签发令牌时写入 JWT claims
     * {@code clientType}，供主应用按终端维度审计。缺省时不写入 claims。
     *
     * @since 3.3.0
     */
    @Schema(description = "客户端类型（可选）：PC 管理端固定 PC_ADMIN")
    private String clientType;
}
