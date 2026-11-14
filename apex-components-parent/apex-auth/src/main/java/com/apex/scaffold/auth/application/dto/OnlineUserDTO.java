package com.apex.scaffold.auth.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 在线用户数据传输对象
 *
 * <p>用于前端与后端之间传输在线用户信息，包含用户基本信息、会话信息、在线时长等。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "在线用户信息")
public class OnlineUserDTO {

    @Schema(description = "用户ID", example = "1001")
    private Long userId;

    @Schema(description = "用户名", example = "zhangsan")
    private String username;

    @Schema(description = "会话ID", example = "1")
    private Long sessionId;

    @Schema(description = "会话唯一标识", example = "session_abc123")
    private String sessionIdentifier;

    @Schema(description = "设备类型", example = "WEB")
    private String deviceType;

    @Schema(description = "客户端IP地址", example = "192.168.1.100")
    private String clientIp;

    @Schema(description = "登录时间", example = "2024-01-01T10:00:00")
    private LocalDateTime loginTime;

    @Schema(description = "最后活跃时间", example = "2024-01-01T12:00:00")
    private LocalDateTime lastActiveAt;

    @Schema(description = "在线时长（分钟）", example = "120")
    private Long onlineDurationMinutes;
}
