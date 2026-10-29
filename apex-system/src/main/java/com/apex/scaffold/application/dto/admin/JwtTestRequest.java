package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JWT 测试请求 DTO。
 *
 * <p>用于测试 JWT 配置是否正确，生成测试 Token 并验证配置的有效性。
 * 该功能主要用于管理员在配置 JWT 参数后，快速验证配置是否生效。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>新租户初始化 JWT 配置后的验证</li>
 *   <li>修改 JWT 签名算法或密钥后的测试</li>
 *   <li>排查 JWT 相关问题时的诊断工具</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>生成的测试 Token 仅用于验证配置，不应用于生产环境的身份认证。
 * 测试 Token 的有效期应设置较短，避免安全风险。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see JwtTestResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "JWT 测试请求")
public class JwtTestRequest {

    /**
     * 测试载荷（Payload）。
     *
     * <p>用于生成测试 Token 的自定义载荷数据，支持以下类型：
     * <ul>
     *   <li>String - 字符串类型</li>
     *   <li>Integer - 整数类型</li>
     *   <li>Long - 长整数类型</li>
     *   <li>Boolean - 布尔类型</li>
     * </ul>
     *
     * <p>常用载荷字段：
     * <ul>
     *   <li>{@code userId}: 用户ID</li>
     *   <li>{@code username}: 用户名</li>
     *   <li>{@code tenantId}: 租户ID</li>
     *   <li>{@code roles}: 用户角色</li>
     * </ul>
     *
     * <p>注意：
     * <ul>
     *   <li>不要在载荷中存储敏感信息（如密码、私钥）</li>
     *   <li>载荷数据会被 Base64 编码，任何人都可以解码查看</li>
     *   <li>标准的 JWT Claims（如 exp、iat、iss）会自动添加，无需手动指定</li>
     * </ul>
     *
     * <p><strong>示例</strong></p>
     * <pre>{@code
     * {
     *   "userId": 1,
     *   "username": "admin",
     *   "tenantId": "default",
     *   "roles": ["ADMIN", "USER"]
     * }
     * }</pre>
     */
    @Schema(
            description = "测试载荷（Payload），支持 String/Integer/Long/Boolean 类型",
            example = "{\"userId\": 1, \"username\": \"admin\", \"tenantId\": \"default\"}",
            required = true)
    @NotNull(message = "测试载荷不能为空")
    private Map<String, Object> payload;

    /**
     * 过期时间（秒）。
     *
     * <p>指定生成的测试 Token 的有效期，单位为秒。
     * 如果不指定，将使用租户 JWT 配置中的 {@code accessTokenTtl} 值。
     *
     * <p>推荐配置：
     * <ul>
     *   <li>测试场景：300（5分钟）</li>
     *   <li>调试场景：600（10分钟）</li>
     *   <li>演示场景：1800（30分钟）</li>
     * </ul>
     *
     * <p>注意：不建议设置过长的有效期，避免测试 Token 被误用。
     */
    @Schema(description = "过期时间（秒），不指定则使用配置的默认值", example = "3600")
    private Long expiresIn;
}
