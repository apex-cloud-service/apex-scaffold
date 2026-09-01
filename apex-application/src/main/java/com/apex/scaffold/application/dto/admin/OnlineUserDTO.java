package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 在线用户 DTO。
 *
 * <p>在线用户列表的展示模型，由 {@code UserEntity} 转换而来，
 * 剔除敏感字段（如密码哈希），仅暴露会话管理所需信息。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@Schema(description = "在线用户")
@Data
@AllArgsConstructor
public class OnlineUserDTO {

    /** 用户 ID */
    @Schema(description = "用户ID")
    private Long id;

    /** 登录账号（账号密码登录用户） */
    @Schema(description = "登录账号")
    private String username;

    /** 昵称 */
    @Schema(description = "昵称")
    private String nickName;

    /** 手机号 */
    @Schema(description = "手机号")
    private String phone;

    /** 最后登录 IP */
    @Schema(description = "最后登录IP")
    private String lastLoginIp;

    /** 最后心跳时间（在线判定依据） */
    @Schema(description = "最后心跳时间")
    private LocalDateTime lastHeartbeatTime;

    /** 无参构造（JSON 反序列化） */
    public OnlineUserDTO() {}
}
