package com.apex.scaffold.common.util;

import com.apex.scaffold.common.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;

/**
 * JWT 工具类
 *
 * <p>提供 JWT Token 的生成、解析和验证功能。密钥通过环境变量 {@code JWT_SECRET} 配置，
 * 生产环境必须显式设置密钥，不允许使用默认密钥。
 *
 * <h2>密钥配置优先级</h2>
 * <ol>
 *   <li>环境变量 {@code JWT_SECRET}</li>
 *   <li>系统属性 {@code jwt.secret}</li>
 *   <li>默认开发密钥（仅非生产环境可用）</li>
 * </ol>
 *
 * <h2>安全要求</h2>
 * <ul>
 *   <li><b>生产环境强制检查</b>：必须通过环境变量 {@code JWT_SECRET} 配置密钥，否则抛出 {@link IllegalStateException}</li>
 *   <li><b>密钥长度要求</b>：默认至少 32 位字符，可通过 {@link #applyConfig(JwtProperties)} 覆盖</li>
 *   <li><b>Token 默认过期时间</b>：7 天，可通过 {@link #applyConfig(JwtProperties)} 覆盖</li>
 * </ul>
 *
 * <h2>运行时配置</h2>
 * <p>Spring 容器启动后通过 {@link #applyConfig(JwtProperties)} 注入 YAML 配置覆盖硬编码默认值。
 * 在此之前使用内嵌的兼容默认值（7 天过期、32 位密钥最小长度）。</p>
 *
 * <h2>异常传递说明</h2>
 * <p>{@link #parseToken} 方法直接传递 JWT 库原始异常（如 {@link ExpiredJwtException}、
 * {@link io.jsonwebtoken.JwtException}），不进行包装，以便调用方按异常类型精确处理。
 *
 * @author Apex Team
 * @version 2.1.0
 * @since 1.0.0
 */
@Slf4j
public class JwtUtil {

    /** 私有构造函数，防止实例化 */
    private JwtUtil() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /** 生产环境配置标识 */
    private static final String PROFILE_PROD = "prod";

    private static final String PROFILE_PRODUCTION = "production";

    /** 内嵌兼容默认值：过期时间（7 天），单位毫秒，会被 {@link #applyConfig(JwtProperties)} 覆盖 */
    private static final long FALLBACK_DEFAULT_EXPIRE_TIME = 7L * 24 * 60 * 60 * 1000;

    /** 内嵌兼容默认值：最小密钥长度，会被 {@link #applyConfig(JwtProperties)} 覆盖 */
    private static final int FALLBACK_MIN_SECRET_LENGTH = 32;

    /**
     * 运行时配置持有者。
     *
     * <p>存储通过 {@link #applyConfig(JwtProperties)} 注入的配置值。
     * 默认使用内嵌兼容值，仅在 Spring 容器启动并调用 {@code applyConfig} 后才切换为 YAML 配置的值。
     */
    private static class ConfigHolder {
        static volatile long defaultExpireTime = FALLBACK_DEFAULT_EXPIRE_TIME;
        static volatile int minSecretLength = FALLBACK_MIN_SECRET_LENGTH;
        static volatile String secret = null; // 从配置文件读取的密钥
        static volatile boolean configured = false;
    }

    /**
     * 注入外部配置覆盖内嵌默认值。
     *
     * <p>由 Spring {@code @Configuration} 类在容器启动后调用，将 YAML 配置文件中的
     * {@code jwt.default-expire-time} 和 {@code jwt.min-secret-length} 注入到工具类中。
     * 仅在首次调用时生效，重复调用会被忽略并输出警告日志。
     *
     * @param properties JWT 配置属性对象
     */
    public static void applyConfig(JwtProperties properties) {
        if (properties == null) {
            log.warn("JwtUtil.applyConfig 收到 null 参数，保持当前默认值");
            return;
        }
        if (ConfigHolder.configured) {
            log.warn(
                    "JwtUtil.applyConfig 重复调用将被忽略，当前 defaultExpireTime={}ms, minSecretLength={}",
                    ConfigHolder.defaultExpireTime,
                    ConfigHolder.minSecretLength);
            return;
        }
        if (properties.getDefaultExpireTime() > 0) {
            ConfigHolder.defaultExpireTime = properties.getDefaultExpireTime();
        }
        if (properties.getMinSecretLength() > 0) {
            ConfigHolder.minSecretLength = properties.getMinSecretLength();
        }
        // 从配置类获取密钥(如果配置了的话)
        try {
            String configuredSecret = properties.getRequiredSecret();
            if (configuredSecret != null && !configuredSecret.isEmpty()) {
                ConfigHolder.secret = configuredSecret;
                log.info("从配置文件加载JWT密钥");
            }
        } catch (IllegalStateException e) {
            log.debug("JWT密钥未在配置文件中设置,将依赖环境变量或系统属性");
        }
        ConfigHolder.configured = true;
        log.info(
                "JwtUtil 已应用外部配置: defaultExpireTime={}ms, minSecretLength={}, secretConfigured={}",
                ConfigHolder.defaultExpireTime,
                ConfigHolder.minSecretLength,
                ConfigHolder.secret != null);
    }

    /**
     * 获取当前生效的默认过期时间（毫秒）。
     *
     * @return 默认过期时间
     */
    static long getConfiguredDefaultExpireTime() {
        return ConfigHolder.defaultExpireTime;
    }

    /**
     * 获取当前生效的最小密钥长度。
     *
     * @return 最小密钥长度
     */
    static int getConfiguredMinSecretLength() {
        return ConfigHolder.minSecretLength;
    }

    /**
     * 密钥延迟加载持有者
     *
     * <p>使用静态内部类实现延迟初始化，确保在首次访问时才计算密钥，
     * 避免类加载时 {@code System.getProperty("spring.profiles.active")} 尚未就绪的问题。
     */
    private static class SecretHolder {
        /** 实际使用的密钥（延迟计算） */
        static final String ACTUAL_SECRET = getActualSecret();
    }

    /**
     * 获取实际使用的密钥（内部方法）。
     *
     * <p>通过 {@link SecretHolder} 实现延迟初始化。
     *
     * @return 密钥字符串
     */
    private static String getActualSecretInternal() {
        return SecretHolder.ACTUAL_SECRET;
    }

    /**
     * 解析当前激活的 Spring Profile。
     *
     * <p>优先读取系统属性 {@code spring.profiles.active}（IDE/命令行 {@code -D} 方式），
     * 为空时回退读取环境变量 {@code SPRING_PROFILES_ACTIVE}（容器/部署脚本方式），
     * 确保两种生产部署方式都能被识别，避免生产环境误用默认开发密钥。</p>
     *
     * @return 激活的 profile 列表（逗号分隔），无则返回空字符串
     */
    static String resolveActiveProfiles() {
        String systemProperty = System.getProperty("spring.profiles.active", "");
        if (!systemProperty.isBlank()) {
            return systemProperty;
        }
        String envVariable = System.getenv("SPRING_PROFILES_ACTIVE");
        return envVariable == null ? "" : envVariable;
    }

    /**
     * 获取实际使用的密钥
     *
     * <p>按优先级从配置类、环境变量、系统属性中获取。
     * 生产环境下如果未配置任何密钥源,直接抛出异常。
     *
     * @return 密钥字符串
     * @throws IllegalStateException 所有密钥源均未配置
     */
    private static String getActualSecret() {
        String activeProfiles = resolveActiveProfiles();
        boolean isProduction = activeProfiles.contains(PROFILE_PROD) || activeProfiles.contains(PROFILE_PRODUCTION);

        // 优先使用配置文件中配置的密钥(通过applyConfig注入)
        if (ConfigHolder.secret != null && !ConfigHolder.secret.isEmpty()) {
            if (isProduction && ConfigHolder.secret.length() < ConfigHolder.minSecretLength) {
                String errorMsg = String.format(
                        "JWT密钥长度不足！要求至少%d位，当前%d位。生产环境必须配置足够长度的密钥！",
                        ConfigHolder.minSecretLength, ConfigHolder.secret.length());
                log.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }

            if (ConfigHolder.secret.length() < ConfigHolder.minSecretLength) {
                log.warn("JWT密钥长度不足{}位，存在安全风险。建议配置更长的密钥。", ConfigHolder.minSecretLength);
            }

            log.info("使用配置文件中的JWT密钥");
            return ConfigHolder.secret;
        }

        // 其次使用环境变量配置的密钥
        String envSecret = System.getenv("JWT_SECRET");
        if (envSecret != null && !envSecret.isEmpty()) {
            // 生产环境强制检查密钥长度
            if (isProduction && envSecret.length() < ConfigHolder.minSecretLength) {
                String errorMsg = String.format(
                        "JWT密钥长度不足！要求至少%d位，当前%d位。生产环境必须配置足够长度的JWT_SECRET环境变量！",
                        ConfigHolder.minSecretLength, envSecret.length());
                log.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }

            if (envSecret.length() < ConfigHolder.minSecretLength) {
                log.warn("JWT密钥长度不足{}位，存在安全风险。建议配置更长的密钥。", ConfigHolder.minSecretLength);
            }

            log.info("使用环境变量配置的JWT密钥");
            return envSecret;
        }

        // 最后使用系统属性配置的密钥
        String devSecret = System.getProperty("jwt.secret", "");
        if (!devSecret.isEmpty()) {
            if (isProduction) {
                String errorMsg = "生产环境必须通过环境变量JWT_SECRET或配置文件jwt.secret配置密钥，不允许使用系统属性！";
                log.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }
            log.info("使用系统属性配置的JWT密钥");
            return devSecret;
        }

        // 所有密钥源均未配置
        String errorMsg = "JWT密钥未配置！请通过以下方式之一配置:\n" + "1. 环境变量: export JWT_SECRET=your-secret-key\n"
                + "2. 配置文件: jwt.secret=your-secret-key\n"
                + "生产环境强烈建议使用环境变量方式!";
        log.error(errorMsg);
        throw new IllegalStateException(errorMsg);
    }

    /**
     * 获取密钥对象
     *
     * <p>将密钥字符串通过 HMAC-SHA 算法转换为 {@link SecretKey} 对象。
     *
     * @param secret 密钥字符串
     * @return SecretKey 对象
     */
    private static SecretKey getSecretKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 获取当前使用的密钥字符串。
     *
     * <p>此方法仅用于内部或同一包内的类访问，不应暴露给外部调用方。
     *
     * @return 密钥字符串
     */
    static String getCurrentSecret() {
        return getActualSecretInternal();
    }

    /**
     * 检查是否使用环境变量配置的密钥
     *
     * @return true-使用环境变量密钥，false-使用默认或系统属性密钥
     */
    public static boolean isUsingEnvSecret() {
        String envSecret = System.getenv("JWT_SECRET");
        return envSecret != null && !envSecret.isEmpty();
    }

    /**
     * 检查密钥是否符合安全要求
     *
     * <p>验证密钥长度是否达到最小要求。</p>
     *
     * @return true-密钥安全，false-密钥不安全
     */
    public static boolean isSecretSecure() {
        String secret = getActualSecretInternal();
        return secret != null && secret.length() >= ConfigHolder.minSecretLength;
    }

    /**
     * 获取密钥安全状态描述
     *
     * @return 密钥安全状态描述信息
     */
    public static String getSecretSecurityStatus() {
        String secret = getActualSecretInternal();
        if (secret == null) {
            return "密钥未配置";
        }
        if (secret.length() >= ConfigHolder.minSecretLength) {
            return String.format("密钥安全（长度：%d位）", secret.length());
        }
        return String.format("密钥不安全（长度：%d位，建议至少%d位）", secret.length(), ConfigHolder.minSecretLength);
    }

    /**
     * 生成 Token（使用默认密钥和默认过期时间）
     *
     * @param subject Token 主题（通常是用户 ID 或 OpenID）
     * @return JWT Token 字符串
     */
    public static String generateToken(String subject) {
        return generateToken(subject, getActualSecretInternal(), ConfigHolder.defaultExpireTime);
    }

    /**
     * 生成 Token（使用默认密钥，自定义过期时间）
     *
     * @param subject    Token 主题
     * @param expireTime 过期时间（毫秒）
     * @return JWT Token 字符串
     */
    public static String generateToken(String subject, long expireTime) {
        return generateToken(subject, getActualSecretInternal(), expireTime);
    }

    /**
     * 生成 Token（使用自定义密钥和过期时间）
     *
     * @param subject    Token 主题
     * @param secret     密钥字符串
     * @param expireTime 过期时间（毫秒）
     * @return JWT Token 字符串
     */
    public static String generateToken(String subject, String secret, long expireTime) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expireTime);

        return Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(expireDate)
                .signWith(getSecretKey(secret), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 生成 Token（带自定义声明，使用默认密钥和默认过期时间）
     *
     * @param claims 自定义声明键值对
     * @return JWT Token 字符串
     */
    public static String generateToken(Map<String, Object> claims) {
        return generateToken(claims, getActualSecretInternal(), ConfigHolder.defaultExpireTime);
    }

    /**
     * 生成 Token（带自定义声明，使用默认密钥，自定义过期时间）
     *
     * @param claims     自定义声明键值对
     * @param expireTime 过期时间（毫秒）
     * @return JWT Token 字符串
     */
    public static String generateToken(Map<String, Object> claims, long expireTime) {
        return generateToken(claims, getActualSecretInternal(), expireTime);
    }

    /**
     * 生成 Token（指定主题与声明，使用默认密钥，自定义过期时间）
     *
     * <p>自动设置 JWT ID（jti）为 UUID 去连字符后的值，用于 Token 黑名单
     * 和强制下线等场景唯一标识 Token。</p>
     *
     * @param subject    Token 主题
     * @param claims     自定义声明键值对
     * @param expireTime 过期时间（毫秒）
     * @return JWT Token 字符串
     */
    public static String generateToken(String subject, Map<String, Object> claims, long expireTime) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expireTime);

        return Jwts.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .subject(subject)
                .claims(claims)
                .issuedAt(now)
                .expiration(expireDate)
                .signWith(getSecretKey(getActualSecretInternal()))
                .compact();
    }

    /**
     * 生成 Token（带自定义声明，使用自定义密钥和过期时间）
     *
     * <p>自动设置 JWT ID（jti）为 UUID 去连字符后的值。</p>
     *
     * @param claims     自定义声明键值对
     * @param secret     密钥字符串
     * @param expireTime 过期时间（毫秒）
     * @return JWT Token 字符串
     */
    public static String generateToken(Map<String, Object> claims, String secret, long expireTime) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expireTime);

        return Jwts.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .claims(claims)
                .issuedAt(now)
                .expiration(expireDate)
                .signWith(getSecretKey(secret))
                .compact();
    }

    /**
     * 解析 Token（使用默认密钥）
     *
     * <p>直接传递 JWT 库的原始异常，调用方应捕获 {@link ExpiredJwtException}、
     * {@link io.jsonwebtoken.JwtException} 等具体异常类型进行处理。
     *
     * @param token JWT Token 字符串
     * @return Claims 对象，包含 Token 中的所有声明
     * @throws ExpiredJwtException Token 已过期
     * @throws io.jsonwebtoken.JwtException Token 签名无效或格式错误
     */
    public static Claims parseToken(String token) {
        return parseToken(token, getActualSecretInternal());
    }

    /**
     * 解析 Token（使用自定义密钥）
     *
     * <p>直接抛出 JWT 库的原始异常，不进行包装，以便调用方按异常类型精确处理。
     *
     * @param token  JWT Token 字符串，不能为 null 或空
     * @param secret 密钥字符串，不能为 null
     * @return Claims 对象，包含 Token 中的所有声明
     * @throws IllegalArgumentException 如果 token 或 secret 为 null
     * @throws io.jsonwebtoken.ExpiredJwtException Token 已过期
     * @throws io.jsonwebtoken.JwtException Token 签名无效或格式错误
     */
    public static Claims parseToken(String token, String secret) {
        if (token == null || token.isEmpty()) {
            throw new JwtException("Token无效或已过期");
        }
        if (secret == null) {
            throw new IllegalArgumentException("密钥不能为 null");
        }
        return Jwts.parser()
                .verifyWith(getSecretKey(secret))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 获取 Token 主题
     *
     * @param token JWT Token 字符串
     * @return Token 主题（sub 字段值）
     */
    public static String getSubject(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * 检查 Token 是否过期
     *
     * @param token JWT Token 字符串
     * @return true-已过期，false-未过期
     */
    public static boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException e) {
            return true;
        }
    }

    /**
     * 验证 Token 是否有效（同时校验签名和过期时间）
     *
     * <p>使用 {@link Jwts#parser()} 的 {@code verifyWith} 验证签名，
     * 同时通过 {@code parseSignedClaims} 检查 Token 是否过期。
     * 签名无效或已过期的 Token 均返回 false。
     *
     * @param token JWT Token 字符串
     * @return true-有效（签名正确且未过期），false-签名无效或已过期
     */
    public static boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}
