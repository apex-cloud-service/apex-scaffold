package com.apex.scaffold.tools.deployment;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 部署安全密钥与前置配置校验：只读取环境变量映射，不输出、不回显任何密钥值。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public final class DeploymentKeyValidator {

    /** 需要 Base64 编码且解码后正好 32 字节的对称密钥。 */
    public static final List<String> BASE64_KEYS =
            List.of("APEX_DATA_ENCRYPTION_KEY", "SECURITY_ENCRYPTION_KEY", "APEX_CONFIG_ENCRYPTION_KEY");

    /** 需要非空白 32 字节 UTF-8 字符串的配置加密密钥。 */
    public static final String CONFIG_ENCRYPTION_KEY = "CONFIG_ENCRYPTION_KEY";

    /** 需要至少 32 字节且不得为模板占位值的 JWT 签名密钥。 */
    public static final String JWT_SECRET = "JWT_SECRET";

    /** 内部服务间认证签名密钥：需至少 32 字节且不得为模板占位值。 */
    public static final String INTERNAL_AUTH_SECRET = "APEX_INTERNAL_AUTH_SECRET";

    /** Compose 前置检查目标：Redis 密码不得使用模板回退值。 */
    public static final String TARGET_COMPOSE = "compose";

    /** 数据源前置检查目标：生产库地址不得缺失。 */
    public static final String TARGET_NACOS_DATASOURCE_PROD = "nacos-datasource-prod";

    /** 对称密钥要求的字节长度。 */
    private static final int KEY_LENGTH = 32;

    /** 严格 Base64 校验：长度必须为 4 的倍数，且仅允许标准字母表与尾部填充。 */
    private static final String BASE64_PATTERN = "(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?";

    /** 必须拒绝的 JWT 模板占位值（小写比较）。 */
    private static final List<String> PLACEHOLDER_SECRETS =
            List.of("change-me", "change_me", "please-change", "changeme");

    /** Compose 模板中 Redis 密码的占位回退值。 */
    private static final String PLACEHOLDER_REDIS_PASSWORD = "apex-redis-password";

    /** 未解析的占位符前缀。 */
    private static final String UNRESOLVED_PLACEHOLDER = "${";

    /** 下一行字符（NEL），部分环境变量编辑器会写入，语义上等同空白。 */
    private static final int NEL = 0x85;

    private DeploymentKeyValidator() {}

    /**
     * 严格解码 32 字节 Base64 密钥。
     *
     * @param value Base64 文本
     * @return 解码后的密钥字节
     */
    public static byte[] decodeKey(String value) {
        if (value == null || !value.matches(BASE64_PATTERN)) {
            throw new IllegalArgumentException("需要 Base64 编码的 32 字节密钥");
        }
        byte[] key = Base64.getDecoder().decode(value);
        if (key.length != KEY_LENGTH) {
            throw new IllegalArgumentException("需要 Base64 编码的 32 字节密钥");
        }
        return key;
    }

    /**
     * 读取环境变量文本值；缺失时返回空串，非字符串值视为配置错误。
     *
     * @param environment 环境变量映射
     * @param name        变量名
     * @return 变量值，缺失时为空串
     */
    public static String value(Map<?, ?> environment, String name) {
        Object value = environment.get(name);
        if (value == null) {
            return "";
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("环境变量类型无效");
        }
        return text;
    }

    /**
     * 判断文本是否只由空白字符组成（含不换行空格与 NEL）。
     *
     * @param text 待判断文本
     * @return 全部为空白字符时返回 true
     */
    public static boolean blank(String text) {
        return text.codePoints()
                .allMatch(codePoint ->
                        Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || codePoint == NEL);
    }

    /**
     * 校验全部部署安全密钥：三段 32 字节 Base64 密钥、32 字节配置加密口令、非模板 JWT 密钥。
     *
     * <p>内部服务认证密钥 {@code APEX_INTERNAL_AUTH_SECRET} 由 compose 模板的
     * {@code ${VAR:?...}} 强制必填并在 {@code compose check} 阶段拦截，此处不重复校验，
     * 避免与编排层口径分叉。
     *
     * @param environment 环境变量映射
     */
    public static void validateKeys(Map<?, ?> environment) {
        for (String name : BASE64_KEYS) {
            try {
                decodeKey(value(environment, name));
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException(name + ": 需要 Base64 编码的 32 字节密钥");
            }
        }
        String config = value(environment, CONFIG_ENCRYPTION_KEY);
        if (blank(config) || config.getBytes(StandardCharsets.UTF_8).length != KEY_LENGTH) {
            throw new IllegalArgumentException(CONFIG_ENCRYPTION_KEY + ": 需要非空白的 32 字节 UTF-8 字符串");
        }
        String jwt = value(environment, JWT_SECRET);
        String lower = jwt.toLowerCase(Locale.ROOT);
        boolean placeholder = PLACEHOLDER_SECRETS.stream().anyMatch(lower::contains);
        if (jwt.getBytes(StandardCharsets.UTF_8).length < KEY_LENGTH || placeholder) {
            throw new IllegalArgumentException(JWT_SECRET + ": 需要至少 32 字节且不能使用模板占位值");
        }
    }

    /**
     * 校验指定目标的前置配置值：必须显式配置，不能是模板回退或未解析占位符。
     *
     * @param environment 环境变量映射
     * @param target      检查目标，取值为 {@link #TARGET_COMPOSE} 或 {@link #TARGET_NACOS_DATASOURCE_PROD}
     */
    public static void validatePreflight(Map<?, ?> environment, String target) {
        boolean compose = TARGET_COMPOSE.equals(target);
        String name = compose ? "REDIS_PASSWORD" : "DB_HOST_PROD";
        String current = value(environment, name);
        boolean placeholderFallback = compose && PLACEHOLDER_REDIS_PASSWORD.equals(current);
        if (blank(current) || current.contains(UNRESOLVED_PLACEHOLDER) || placeholderFallback) {
            throw new IllegalArgumentException(name + ": 需要显式配置非空值，不能使用模板回退或未解析占位符");
        }
    }
}
