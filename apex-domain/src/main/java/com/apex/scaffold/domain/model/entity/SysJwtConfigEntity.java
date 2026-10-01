package com.apex.scaffold.domain.model.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 系统 JWT 配置领域实体。
 *
 * <p>表示系统中 JWT 令牌的签发和验证配置，包括签名算法、密钥管理、令牌有效期等核心参数。
 * 支持对称加密（HS256）和非对称加密（RS256）两种签名算法。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>管理 JWT 签发者标识和签名密钥</li>
 *   <li>定义访问令牌和刷新令牌的有效期</li>
 *   <li>支持 HS256 对称加密和 RS256 非对称加密</li>
 *   <li>控制 JWT 配置的启用状态</li>
 *   <li>实现租户级别的 JWT 配置隔离</li>
 * </ul>
 *
 * <h2>签名算法说明</h2>
 * <ul>
 *   <li><strong>HS256</strong>：对称加密，使用 {@link #secretKey} 进行签名和验证，
 *       适用于单体应用或可信内部服务</li>
 *   <li><strong>RS256</strong>：非对称加密，使用 {@link #privateKey} 签名、
 *       {@link #publicKey} 验证，适用于分布式系统和第三方集成</li>
 * </ul>
 *
 * <h2>安全约束</h2>
 * <ul>
 *   <li>{@link #secretKey} 和 {@link #privateKey} 必须使用 AES 加密存储，密钥从环境变量读取</li>
 *   <li>禁止在日志、响应体或异常信息中输出明文密钥</li>
 *   <li>更新密钥时需要考虑现有令牌的有效性和平滑过渡策略</li>
 *   <li>密钥强度应满足安全规范：HS256 密钥至少 256 位，RSA 私钥至少 2048 位</li>
 * </ul>
 *
 * <h2>租户隔离</h2>
 * <p>每个租户可以配置独立的 JWT 签名策略，包括：
 * <ul>
 *   <li>独立的签发者标识</li>
 *   <li>独立的签名密钥或密钥对</li>
 *   <li>独立的令牌有效期策略</li>
 * </ul>
 *
 * <h2>业务规则</h2>
 * <ul>
 *   <li>访问令牌有效期通常为 15 分钟到 1 小时</li>
 *   <li>刷新令牌有效期通常为 7 天到 30 天</li>
 *   <li>刷新令牌有效期必须大于访问令牌有效期</li>
 *   <li>禁用配置后，新令牌签发将失败，但已签发令牌仍然有效直到过期</li>
 *   <li>切换签名算法时需要保证密钥配置的完整性</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.infrastructure.persistence.po.SysJwtConfigPO
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class SysJwtConfigEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * JWT 签发者标识。
     *
     * <p>用于标识令牌的签发方，通常是应用名称或服务域名（如 "apex-scaffold" 或 "https://auth.example.com"）。
     * 接收方可以通过验证 {@code iss} 声明来确认令牌来源的合法性。
     *
     * <p>长度限制 200 字符，必填。
     */
    private String issuer;

    /**
     * 签名密钥（AES 加密存储）。
     *
     * <p><strong>安全规范</strong>：
     * <ul>
     *   <li>用于 HS256 对称加密算法的签名和验证</li>
     *   <li>存储前必须使用 AES 算法加密，密钥从环境变量 {@code JWT_SECRET_KEY} 读取</li>
     *   <li>密钥长度至少 256 位（32 字节），推荐使用随机生成的强密钥</li>
     *   <li>禁止在日志或响应中输出明文密钥</li>
     *   <li>仅在签名和验证时临时解密使用</li>
     * </ul>
     *
     * <p>当 {@link #algorithm} 为 "HS256" 时必填，加密后长度最多 500 字符。
     *
     * @see #algorithm
     */
    private String secretKey;

    /**
     * 访问令牌有效期（秒）。
     *
     * <p>定义访问令牌（Access Token）的生命周期，过期后客户端需要使用刷新令牌重新获取。
     * 通常设置为较短的时间以降低令牌泄露风险。
     *
     * <p>常见配置：
     * <ul>
     *   <li>15 分钟（900 秒）- 高安全场景</li>
     *   <li>30 分钟（1800 秒）- 平衡场景</li>
     *   <li>1 小时（3600 秒）- 低频交互场景</li>
     * </ul>
     *
     * <p>默认值 3600 秒（1 小时），必填。
     */
    private Integer accessTokenTtl;

    /**
     * 刷新令牌有效期（秒）。
     *
     * <p>定义刷新令牌（Refresh Token）的生命周期，用于在访问令牌过期后获取新的访问令牌，
     * 避免用户频繁重新登录。刷新令牌通常设置为较长的时间。
     *
     * <p>常见配置：
     * <ul>
     *   <li>7 天（604800 秒）- 短期会话</li>
     *   <li>14 天（1209600 秒）- 中期会话</li>
     *   <li>30 天（2592000 秒）- 长期会话</li>
     * </ul>
     *
     * <p>默认值 86400 秒（1 天），必填。业务规则要求必须大于 {@link #accessTokenTtl}。
     */
    private Integer refreshTokenTtl;

    /**
     * 签名算法。
     *
     * <p>支持以下算法：
     * <ul>
     *   <li><strong>HS256</strong>：HMAC-SHA256 对称加密，使用 {@link #secretKey} 签名和验证</li>
     *   <li><strong>RS256</strong>：RSA-SHA256 非对称加密，使用 {@link #privateKey} 签名、
     *       {@link #publicKey} 验证</li>
     * </ul>
     *
     * <p>默认值 "HS256"，长度限制 20 字符，必填。
     *
     * @see #secretKey
     * @see #publicKey
     * @see #privateKey
     */
    private String algorithm;

    /**
     * RSA 公钥（PEM 格式）。
     *
     * <p>用于 RS256 非对称加密算法的令牌验证。公钥可以公开分发给令牌验证方，
     * 无需保密。格式示例：
     * <pre>
     * -----BEGIN PUBLIC KEY-----
     * MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
     * -----END PUBLIC KEY-----
     * </pre>
     *
     * <p>当 {@link #algorithm} 为 "RS256" 时必填，使用 TEXT 类型存储。
     *
     * @see #algorithm
     * @see #privateKey
     */
    private String publicKey;

    /**
     * RSA 私钥（PEM 格式，AES 加密存储）。
     *
     * <p><strong>安全规范</strong>：
     * <ul>
     *   <li>用于 RS256 非对称加密算法的令牌签名</li>
     *   <li>存储前必须使用 AES 算法加密，密钥从环境变量 {@code JWT_PRIVATE_KEY} 读取</li>
     *   <li>密钥长度至少 2048 位，推荐 4096 位</li>
     *   <li>禁止在日志或响应中输出明文私钥</li>
     *   <li>仅在签名时临时解密使用</li>
     * </ul>
     *
     * <p>格式示例：
     * <pre>
     * -----BEGIN PRIVATE KEY-----
     * MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC...
     * -----END PRIVATE KEY-----
     * </pre>
     *
     * <p>当 {@link #algorithm} 为 "RS256" 时必填，使用 TEXT 类型存储。
     *
     * @see #algorithm
     * @see #publicKey
     */
    private String privateKey;

    /**
     * 是否启用。
     *
     * <p>控制 JWT 配置是否生效：
     * <ul>
     *   <li>0 - 禁用：新令牌签发将失败，但已签发令牌仍然有效直到过期</li>
     *   <li>1 - 启用：正常签发和验证令牌</li>
     * </ul>
     *
     * <p>默认值 1（启用），必填。
     */
    private Integer enabled;

    /**
     * 租户标识。
     *
     * <p>实现多租户数据隔离，确保不同租户的 JWT 配置相互独立。
     * 所有查询和操作都必须携带租户标识进行过滤。
     *
     * <p>长度限制 64 字符，必填。
     */
    private String tenantId;

    /**
     * 无参构造函数。
     *
     * <p>用于框架反射创建实例和持久化层映射。
     */
    public SysJwtConfigEntity() {
        super();
    }

    /**
     * 设置 JWT 签发者标识。
     *
     * @param issuer JWT 签发者标识
     */
    public void setIssuer(String issuer) {
        this.issuer = issuer;
        touch();
    }

    /**
     * 设置签名密钥（已加密）。
     *
     * @param secretKey 签名密钥（AES 加密后）
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
        touch();
    }

    /**
     * 设置访问令牌有效期。
     *
     * @param accessTokenTtl 访问令牌有效期（秒）
     */
    public void setAccessTokenTtl(Integer accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
        touch();
    }

    /**
     * 设置刷新令牌有效期。
     *
     * @param refreshTokenTtl 刷新令牌有效期（秒）
     */
    public void setRefreshTokenTtl(Integer refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
        touch();
    }

    /**
     * 设置签名算法。
     *
     * @param algorithm 签名算法（HS256/RS256）
     */
    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
        touch();
    }

    /**
     * 设置 RSA 公钥。
     *
     * @param publicKey RSA 公钥（PEM 格式）
     */
    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
        touch();
    }

    /**
     * 设置 RSA 私钥（已加密）。
     *
     * @param privateKey RSA 私钥（PEM 格式，AES 加密后）
     */
    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
        touch();
    }

    /**
     * 设置启用状态。
     *
     * @param enabled 是否启用（0-禁用，1-启用）
     */
    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
        touch();
    }

    /**
     * 设置租户标识。
     *
     * @param tenantId 租户标识
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
        touch();
    }
}
