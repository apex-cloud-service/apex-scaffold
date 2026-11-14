package com.apex.scaffold.auth.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 密码找回确认重设响应。
 *
 * <p>撤销会话失败时不能伪称全端退出：sessionRevocation 返回 PENDING，
 * 撤销仍在处理，由运维侧跟踪日志完成补偿。</p>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "密码找回确认重设响应")
public class PasswordRecoveryConfirmResponse {

    @Schema(description = "重设结果状态", example = "PASSWORD_RESET")
    private String status;

    @Schema(description = "会话撤销状态：COMPLETED-已完成全端撤销，PENDING-仍在处理", example = "COMPLETED")
    private String sessionRevocation;
}
