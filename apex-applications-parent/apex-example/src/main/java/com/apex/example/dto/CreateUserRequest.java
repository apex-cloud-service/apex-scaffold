package com.apex.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建用户请求DTO
 *
 * <p>展示如何使用JSR-303注解进行参数校验
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ExampleCreateUserRequest", description = "示例模块创建/更新用户请求；展示 Jakarta Validation 与 @Schema 配合写法")
public class CreateUserRequest {

    /**
     * 用户名(3-20个字符)
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度必须在3-20个字符之间")
    @Schema(
            description = "用户名，3-20 个字符，不能为空白",
            example = "zhangsan",
            minLength = 3,
            maxLength = 20,
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    /**
     * 邮箱
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱地址，须符合邮箱格式", example = "zhangsan@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    /**
     * 年龄(18-100)
     */
    @Min(value = 18, message = "年龄必须大于等于18岁")
    @Max(value = 100, message = "年龄不能超过100岁")
    @Schema(
            description = "年龄，18-100",
            example = "28",
            type = "integer",
            format = "int32",
            minimum = "18",
            maximum = "100")
    private Integer age;

    /**
     * 手机号
     */
    @NotBlank(message = "手机号不能为空")
    @Size(min = 11, max = 11, message = "手机号必须为11位")
    @Schema(
            description = "手机号，11 位",
            example = "13800138000",
            minLength = 11,
            maxLength = 11,
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String phone;
}
