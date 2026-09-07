package com.apex.scaffold.application.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库配置请求 DTO。
 *
 * <p>用于数据库连接测试和部署配置生成，密码仅用于一次性操作，不会被保存。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.2.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseConfigRequest {

    /**
     * 数据库类型。
     */
    @NotBlank(message = "数据库类型不能为空")
    private String dbType;

    /**
     * 主机地址。
     */
    @NotBlank(message = "主机地址不能为空")
    private String host;

    /**
     * 端口。
     */
    @NotNull(message = "端口不能为空")
    @Min(value = 1, message = "端口必须在 1-65535 之间")
    @Max(value = 65535, message = "端口必须在 1-65535 之间")
    private Integer port;

    /**
     * 数据库名。
     */
    @NotBlank(message = "数据库名不能为空")
    private String database;

    /**
     * 用户名。
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码（仅用于测试和配置生成，不保存）。
     */
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 连接参数（可选）。
     */
    private String connectionParams;

    /**
     * 部署环境。
     */
    @NotBlank(message = "部署环境不能为空")
    private String environment;
}
