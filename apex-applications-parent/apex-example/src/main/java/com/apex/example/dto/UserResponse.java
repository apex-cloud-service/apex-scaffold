package com.apex.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户响应DTO
 *
 * <p>展示如何将实体转换为DTO返回给前端
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ExampleUserResponse", description = "示例模块用户响应")
public class UserResponse {

    /**
     * 用户ID
     */
    @Schema(description = "用户 ID", example = "1", type = "integer", format = "int64")
    private Long id;

    /**
     * 用户名
     */
    @Schema(description = "用户名", example = "zhangsan", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    /**
     * 邮箱
     */
    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;

    /**
     * 年龄
     */
    @Schema(
            description = "年龄，18-100",
            example = "28",
            type = "integer",
            format = "int32",
            minimum = "18",
            maximum = "100")
    private Integer age;

    /**
     * 手机号(脱敏)
     */
    @Schema(description = "手机号（已脱敏）", example = "138****8000")
    private String phone;

    /**
     * 状态
     */
    @Schema(
            description = "状态：0=正常，1=禁用",
            example = "0",
            allowableValues = {"0", "1"},
            type = "integer",
            format = "int32")
    private Integer status;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", example = "2026-11-22T10:15:30", type = "string", format = "date-time")
    private LocalDateTime createTime;
}
