package com.apex.scaffold.domain.model.monitor;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 体检快照签名计算与校验。
 *
 * <p>按固定字段顺序把结论级字段拼接为规范化载荷，用 HMAC-SHA256 计算摘要并转十六进制。
 * 只覆盖会被展示或参与趋势判断的字段，租户与时间戳等由持久化层维护的字段不参与，
 * 避免落库自动填充导致签名不可复现。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@link #INTEGRITY_VALID}：签名存在且与重算结果一致</li>
 *   <li>{@link #INTEGRITY_INVALID}：签名存在但与重算结果不一致，说明落库后被改写</li>
 *   <li>{@link #INTEGRITY_UNVERIFIABLE}：无签名（V70 之前的存量快照）或密钥未配置，无法判定</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.4.0
 */
public final class HealthSnapshotSigner {

    /** 完整性结论：签名有效。 */
    public static final String INTEGRITY_VALID = "VALID";

    /** 完整性结论：签名不匹配。 */
    public static final String INTEGRITY_INVALID = "INVALID";

    /** 完整性结论：无法验证。 */
    public static final String INTEGRITY_UNVERIFIABLE = "UNVERIFIABLE";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final String SEPARATOR = "\n";

    private HealthSnapshotSigner() {}

    /**
     * 计算快照签名。
     *
     * @param entity 快照实体
     * @param secret 签名密钥；为空时返回 {@code null}
     * @return 十六进制签名，密钥为空时返回 {@code null}
     */
    public static String sign(HealthSnapshotEntity entity, String secret) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(payload(entity).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("体检快照签名计算失败", e);
        }
    }

    /**
     * 校验快照签名。
     *
     * @param entity 快照实体
     * @param secret 签名密钥；为空时返回 {@link #INTEGRITY_UNVERIFIABLE}
     * @return 完整性结论
     */
    public static String verify(HealthSnapshotEntity entity, String secret) {
        String expected = sign(entity, secret);
        String actual = entity.getSignature();
        if (expected == null || actual == null || actual.isBlank()) {
            return INTEGRITY_UNVERIFIABLE;
        }
        // 常量时间比较，避免按字节提前返回泄露签名前缀
        boolean matched = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
        return matched ? INTEGRITY_VALID : INTEGRITY_INVALID;
    }

    /**
     * 组装规范化签名载荷。
     *
     * <p>字段顺序与分隔符变更会令全部历史签名失效，必须与 {@code ruleVersion} 一起演进。</p>
     */
    private static String payload(HealthSnapshotEntity entity) {
        return String.join(
                SEPARATOR,
                nullToEmpty(entity.getCaptureSource()),
                nullToEmpty(entity.getServiceName()),
                nullToEmpty(entity.getInstanceId()),
                nullToEmpty(entity.getScope()),
                nullToEmpty(entity.getRuleVersion()),
                nullToEmpty(entity.getOverallStatus()),
                nullToEmpty(entity.getSummary()),
                String.valueOf(nullToZero(entity.getUpCount())),
                String.valueOf(nullToZero(entity.getDegradedCount())),
                String.valueOf(nullToZero(entity.getDownCount())),
                String.valueOf(nullToZero(entity.getUnknownCount())),
                String.valueOf(nullToZero(entity.getNotConfiguredCount())),
                entity.getGeneratedAt() == null
                        ? ""
                        : entity.getGeneratedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
