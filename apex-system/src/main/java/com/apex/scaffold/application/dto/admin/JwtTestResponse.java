package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JWT 测试响应 DTO。
 *
 * <p>包含生成的测试 Token 和解码后的详细信息，用于验证 JWT 配置的正确性。
 * 该响应提供了完整的 Token 生命周期信息，方便管理员排查配置问题。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>生成的 JWT Token（完整字符串）</li>
 *   <li>解码后的 Payload（包含所有 Claims）</li>
 *   <li>Token 有效性验证结果</li>
 *   <li>Token 生命周期信息（签发时间、过期时间）</li>
 *   <li>签发者标识</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <p>管理员可以使用此响应：
 * <ul>
 *   <li>复制生成的 Token 到 JWT 解码工具（如 jwt.io）进行验证</li>
 *   <li>检查 Payload 中的 Claims 是否符合预期</li>
 *   <li>验证 Token 的有效期设置是否正确</li>
 *   <li>确认签发者标识与配置一致</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see JwtTestRequest
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "JWT 测试响应")
public class JwtTestResponse {

    /**
     * 生成的 Token。
     *
     * <p>完整的 JWT Token 字符串，格式为 {@code header.payload.signature}。
     * 该 Token 可用于测试和验证，但不建议在生产环境中使用。
     *
     * <p><strong>Token 结构</strong></p>
     * <ul>
     *   <li>Header: 包含算法类型（如 HS256）和 Token 类型（JWT）</li>
     *   <li>Payload: 包含所有 Claims（标准 Claims + 自定义 Claims）</li>
     *   <li>Signature: 根据 Header 和 Payload 以及密钥生成的签名</li>
     * </ul>
     *
     * <p>示例：
     * <pre>{@code
     * eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.
     * eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4iLCJleHAiOjE3MjcyNzI4MDB9.
     * SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
     * }</pre>
     */
    @Schema(description = "生成的 JWT Token 完整字符串")
    private String token;

    /**
     * 解码后的 Payload。
     *
     * <p>包含 Token 中所有的 Claims（标准 Claims + 自定义 Claims）。
     * 这些数据已经过验证，确保 Token 的签名有效且未被篡改。
     *
     * <p><strong>标准 Claims</strong></p>
     * <ul>
     *   <li>{@code iss}: 签发者（Issuer）</li>
     *   <li>{@code sub}: 主题（Subject）</li>
     *   <li>{@code aud}: 受众（Audience）</li>
     *   <li>{@code exp}: 过期时间（Expiration Time）</li>
     *   <li>{@code nbf}: 生效时间（Not Before）</li>
     *   <li>{@code iat}: 签发时间（Issued At）</li>
     *   <li>{@code jti}: Token ID（JWT ID）</li>
     * </ul>
     *
     * <p><strong>自定义 Claims</strong></p>
     * <p>来自请求中指定的 {@code payload} 字段。
     *
     * <p>示例：
     * <pre>{@code
     * {
     *   "userId": 1,
     *   "username": "admin",
     *   "tenantId": "default",
     *   "iss": "apex-scaffold",
     *   "iat": 1727269200,
     *   "exp": 1727272800
     * }
     * }</pre>
     */
    @Schema(description = "解码后的 Payload，包含所有 Claims")
    private Map<String, Object> decoded;

    /**
     * Token 是否有效。
     *
     * <p>验证结果标识，表示生成的 Token 是否通过以下验证：
     * <ul>
     *   <li>签名验证：签名与 Header + Payload + 密钥计算结果一致</li>
     *   <li>格式验证：Token 格式符合 JWT 标准</li>
     *   <li>时间验证：Token 未过期且已生效</li>
     *   <li>签发者验证：签发者与配置的 issuer 一致</li>
     * </ul>
     *
     * <p>取值：
     * <ul>
     *   <li>{@code true}: Token 有效，配置正确</li>
     *   <li>{@code false}: Token 无效，配置可能存在问题</li>
     * </ul>
     *
     * <p>注意：如果为 {@code false}，应检查 JWT 配置中的算法、密钥、签发者等参数。
     */
    @Schema(description = "Token 是否有效（通过签名和格式验证）", example = "true")
    private Boolean isValid;

    /**
     * 过期时间。
     *
     * <p>Token 的过期时间戳，超过此时间后 Token 将失效。
     * 该时间由 {@code iat}（签发时间）+ {@code expiresIn}（有效期）计算得出。
     *
     * <p>时区说明：返回的是服务器本地时区的时间，通常为 Asia/Shanghai。
     */
    @Schema(description = "Token 过期时间（服务器本地时区）")
    private LocalDateTime expiresAt;

    /**
     * 签发时间。
     *
     * <p>Token 的签发时间戳，表示 Token 的生成时间。
     * 该时间对应 JWT 标准 Claim {@code iat}（Issued At）。
     *
     * <p>时区说明：返回的是服务器本地时区的时间，通常为 Asia/Shanghai。
     */
    @Schema(description = "Token 签发时间（服务器本地时区）")
    private LocalDateTime issuedAt;

    /**
     * 签发者标识。
     *
     * <p>Token 的签发者标识，对应 JWT 标准 Claim {@code iss}（Issuer）。
     * 该值应与租户 JWT 配置中的 {@code issuer} 字段一致。
     *
     * <p>常见配置：
     * <ul>
     *   <li>{@code apex-scaffold}: 默认签发者</li>
     *   <li>{@code https://auth.example.com}: 使用 URL 作为签发者</li>
     *   <li>{@code tenant-{tenantId}}: 租户专属签发者</li>
     * </ul>
     *
     * <p>作用：
     * <ul>
     *   <li>标识 Token 的来源，防止跨系统伪造</li>
     *   <li>支持多租户场景下的 Token 隔离</li>
     *   <li>符合 OAuth2 和 OpenID Connect 规范</li>
     * </ul>
     */
    @Schema(description = "签发者标识（Issuer）", example = "apex-scaffold")
    private String issuer;
}
