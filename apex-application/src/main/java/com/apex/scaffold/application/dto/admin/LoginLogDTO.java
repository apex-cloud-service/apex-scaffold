package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录日志响应 DTO。
 *
 * <p>系统管理-登录日志的分页响应元素，承载单次登录尝试的账号、
 * 来源环境、结果与时间信息。</p>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录日志响应")
/**n * LoginLogDTOn *n * <p>TODO: 补充类的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class LoginLogDTO {

    /**
     * 日志 ID。
     */
    @Schema(description = "日志ID")
    private Long id;

    /**
     * 登录账号。
     */
    @Schema(description = "登录账号")
    private String username;

    /**
     * 客户端 IP 地址。
     */
    @Schema(description = "客户端IP")
    private String ip;

    /**
     * 浏览器信息。
     */
    @Schema(description = "浏览器信息")
    private String browser;

    /**
     * 操作系统信息。
     */
    @Schema(description = "操作系统信息")
    private String os;

    /**
     * 登录状态（0-成功，1-失败）。
     */
    @Schema(description = "登录状态：0-成功，1-失败")
    private Integer status;

    /**
     * 登录消息。
     */
    @Schema(description = "登录消息")
    private String message;

    /**
     * 登录时间。
     */
    @Schema(description = "登录时间")
    private LocalDateTime loginTime;
}
