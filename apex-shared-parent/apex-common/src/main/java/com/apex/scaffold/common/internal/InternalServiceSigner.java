package com.apex.scaffold.common.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 内部服务调用的短时效 HMAC 签名（不依赖用户令牌，不引入额外授权服务器）。
 *
 * <p>签名串固定为 {@code service:timestamp:nonce:method:path}，用共享密钥做 HmacSHA256。
 * 接收端校验时间窗与 nonce 唯一性，因此重放、篡改方法或路径都会失败。租户参数必须
 * 由调用方在签名之外显式声明并在此签名，避免调用方任意跨租户读取。
 *
 * <p>未配置密钥时默认拒绝（fail-closed）；只有显式把 {@code apex.internal-auth.enabled}
 * 设为 false 才允许关闭，用于本地单测或纯开发环境。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
public final class InternalServiceSigner {

    /** 请求头：调用方服务名。 */
    public static final String HEADER_SERVICE = "X-Internal-Service";

    /** 请求头：签名时间戳（epoch 秒）。 */
    public static final String HEADER_TIMESTAMP = "X-Internal-Timestamp";

    /** 请求头：随机串，用于防重放。 */
    public static final String HEADER_NONCE = "X-Internal-Nonce";

    /** 请求头：HmacSHA256 十六进制签名。 */
    public static final String HEADER_SIGNATURE = "X-Internal-Signature";

    /** 请求头：调用方声明的租户作用域（参与签名）。 */
    public static final String HEADER_TENANT = "X-Internal-Tenant";

    private final byte[] secret;

    private final Duration allowedSkew;

    private final Map<String, Instant> nonces = new ConcurrentHashMap<>();

    private final Clock clock;

    /**
     * 构造签名器。
     *
     * @param secret 共享密钥（至少 32 字节）；空白表示未配置
     * @param allowedSkew 允许的时钟偏差
     * @param clock 时钟（测试可注入）
     */
    public InternalServiceSigner(String secret, Duration allowedSkew, Clock clock) {
        this.secret = secret == null || secret.isBlank() ? null : secret.getBytes(StandardCharsets.UTF_8);
        this.allowedSkew = allowedSkew == null ? Duration.ofSeconds(60) : allowedSkew;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * 生成调用所需的签名头。
     *
     * @param service 调用方服务名
     * @param method HTTP 方法
     * @param path 请求路径（含查询串时由调用方决定是否纳入）
     * @param tenant 目标租户（可空）
     * @return 需要附带的请求头
     */
    public Map<String, String> sign(String service, String method, String path, String tenant) {
        return sign(service, method, path, tenant, new byte[0]);
    }

    /** 签名绑定完整原始路径及查询串、租户和已编码的请求正文。 */
    public Map<String, String> sign(String service, String method, String path, String tenant, byte[] body) {
        if (secret == null) {
            throw new IllegalStateException("未配置 apex.internal-auth.secret，无法签名内部调用");
        }
        if (service == null || service.isBlank() || method == null || path == null) {
            throw new IllegalArgumentException("内部调用签名参数不完整");
        }
        String timestamp = String.valueOf(clock.instant().getEpochSecond());
        String nonce = java.util.UUID.randomUUID().toString().replace("-", "");
        String normalizedTenant = tenant == null ? "" : tenant;
        String signature = hmac(payload(service, timestamp, nonce, method, path, normalizedTenant, body));
        Map<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put(HEADER_SERVICE, service);
        headers.put(HEADER_TIMESTAMP, timestamp);
        headers.put(HEADER_NONCE, nonce);
        headers.put(HEADER_SIGNATURE, signature);
        if (!normalizedTenant.isBlank()) {
            headers.put(HEADER_TENANT, normalizedTenant);
        }
        return headers;
    }

    /**
     * 校验一次内部调用；成功后记录 nonce，重复 nonce 视为重放。
     *
     * @param service 调用方服务名
     * @param timestamp 时间戳
     * @param nonce 随机串
     * @param signature 签名
     * @param method HTTP 方法
     * @param path 请求路径
     * @param tenant 目标租户（可空）
     * @return 校验通过返回 true
     */
    public boolean verify(
            String service,
            String timestamp,
            String nonce,
            String signature,
            String method,
            String path,
            String tenant) {
        return verify(service, timestamp, nonce, signature, method, path, tenant, new byte[0]);
    }

    /** 对原始查询串和请求正文做验签，重复请求不得重新消费 nonce。 */
    public boolean verify(
            String service,
            String timestamp,
            String nonce,
            String signature,
            String method,
            String path,
            String tenant,
            byte[] body) {
        if (secret == null
                || service == null
                || service.isBlank()
                || timestamp == null
                || nonce == null
                || nonce.isBlank()
                || signature == null
                || method == null
                || path == null) {
            return false;
        }
        Instant issuedAt;
        try {
            issuedAt = Instant.ofEpochSecond(Long.parseLong(timestamp));
        } catch (NumberFormatException invalid) {
            return false;
        }
        if (Duration.between(issuedAt, clock.instant()).abs().compareTo(allowedSkew) > 0) {
            return false;
        }
        String normalizedTenant = tenant == null ? "" : tenant;
        String expected = hmac(payload(service, timestamp, nonce, method, path, normalizedTenant, body));
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            return false;
        }
        pruneExpiredNonces();
        return nonces.putIfAbsent(nonce, issuedAt.plus(allowedSkew)) == null;
    }

    /** 清理过期 nonce，避免无界增长；调用频率低，直接线性清理即可。 */
    private void pruneExpiredNonces() {
        Instant now = clock.instant();
        nonces.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }

    private String payload(
            String service, String timestamp, String nonce, String method, String path, String tenant, byte[] body) {
        try {
            String digest = HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(body == null ? new byte[0] : body));
            return String.join("\n", "v2", service, timestamp, nonce, method, path, tenant, digest);
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("内部调用签名失败", error);
        }
    }

    /**
     * 是否已配置内部调用签名密钥。
     *
     * @return 已注入非空密钥时返回 {@code true}
     */
    public boolean configured() {
        return secret != null;
    }

    /**
     * 常量时间比较辅助（供测试与调用方复用）。
     *
     * @param left 左值
     * @param right 右值
     * @return 相等返回 true
     */
    public static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                Base64.getEncoder().encode(left.getBytes(StandardCharsets.UTF_8)),
                Base64.getEncoder().encode(right.getBytes(StandardCharsets.UTF_8)));
    }
}
