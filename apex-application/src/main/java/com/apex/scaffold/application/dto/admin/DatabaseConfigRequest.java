package com.apex.scaffold.application.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库配置请求
 *
 * <p>用于数据库连接测试和部署配置生成。此 DTO 仅用于临时操作，
 * 密码等敏感信息在使用后不会被持久化到系统配置中。
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>连接测试：验证数据库连接参数是否正确</li>
 *   <li>配置生成：为不同环境生成数据库配置文件</li>
 *   <li>部署辅助：帮助运维人员快速配置数据库连接</li>
 * </ul>
 *
 * <h2>安全说明</h2>
 * <p>密码字段仅用于一次性操作，系统不会将密码保存到数据库或配置文件中。
 * 生成的配置文件应通过环境变量或安全的配置管理系统来管理敏感信息。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseConfigRequest {

    /**
     * 数据库类型
     *
     * <p>支持的数据库类型。
     *
     * <p>常用值：
     * <ul>
     *   <li>{@code mysql}：MySQL 数据库</li>
     *   <li>{@code postgresql}：PostgreSQL 数据库</li>
     *   <li>{@code oracle}：Oracle 数据库</li>
     *   <li>{@code sqlserver}：SQL Server 数据库</li>
     * </ul>
     */
    @NotBlank(message = "数据库类型不能为空")
    private String dbType;

    /**
     * 主机地址
     *
     * <p>数据库服务器的主机名或 IP 地址。
     *
     * <p>示例：{@code localhost}、{@code 192.168.1.100}、{@code db.example.com}
     */
    @NotBlank(message = "主机地址不能为空")
    private String host;

    /**
     * 端口号
     *
     * <p>数据库服务监听的端口。
     *
     * <p>常用端口：
     * <ul>
     *   <li>MySQL：3306</li>
     *   <li>PostgreSQL：5432</li>
     *   <li>Oracle：1521</li>
     *   <li>SQL Server：1433</li>
     * </ul>
     *
     * <p>验证规则：必须在 1-65535 之间
     */
    @NotNull(message = "端口不能为空")
    @Min(value = 1, message = "端口必须在 1-65535 之间")
    @Max(value = 65535, message = "端口必须在 1-65535 之间")
    private Integer port;

    /**
     * 数据库名
     *
     * <p>要连接的数据库实例名称。
     *
     * <p>示例：{@code apex_scaffold}、{@code production_db}
     */
    @NotBlank(message = "数据库名不能为空")
    private String database;

    /**
     * 用户名
     *
     * <p>用于连接数据库的用户账号。
     *
     * <p>建议：生产环境应使用专用账号，并遵循最小权限原则
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码
     *
     * <p>用于连接数据库的用户密码。
     *
     * <p>安全说明：
     * <ul>
     *   <li>此字段仅用于连接测试和配置生成</li>
     *   <li>密码不会被保存到数据库或日志文件</li>
     *   <li>使用后立即清除内存中的密码</li>
     *   <li>生成的配置文件应通过环境变量注入密码</li>
     * </ul>
     */
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 连接参数（可选）
     *
     * <p>附加的 JDBC 连接参数，以键值对形式追加到连接字符串。
     *
     * <p>示例：
     * <ul>
     *   <li>MySQL：{@code useSSL=false&serverTimezone=Asia/Shanghai}</li>
     *   <li>PostgreSQL：{@code ssl=true&sslmode=require}</li>
     * </ul>
     */
    private String connectionParams;

    /**
     * 部署环境
     *
     * <p>目标部署环境标识，用于生成对应环境的配置文件。
     *
     * <p>常用值：
     * <ul>
     *   <li>{@code dev}：开发环境</li>
     *   <li>{@code test}：测试环境</li>
     *   <li>{@code staging}：预发布环境</li>
     *   <li>{@code production}：生产环境</li>
     * </ul>
     */
    @NotBlank(message = "部署环境不能为空")
    private String environment;
}
