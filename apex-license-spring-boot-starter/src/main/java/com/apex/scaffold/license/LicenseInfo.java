package com.apex.scaffold.license;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * License授权信息
 *
 * <p>包含商业版本的授权详情,用于验证和限制功能使用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@Data
public class LicenseInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * License ID(唯一标识)
     */
    private String licenseId;

    /**
     * 客户名称
     */
    private String customerName;

    /**
     * 客户邮箱
     */
    private String customerEmail;

    /**
     * 授权版本(BASIC/STANDARD/ENTERPRISE)
     */
    private LicenseVersion version;

    /**
     * 授权开始时间
     */
    private LocalDateTime validFrom;

    /**
     * 授权过期时间
     */
    private LocalDateTime validTo;

    /**
     * 最大用户数(-1表示无限制)
     */
    private Integer maxUsers;

    /**
     * 最大并发数(-1表示无限制)
     */
    private Integer maxConcurrent;

    /**
     * 启用的功能模块(JSON数组)
     * <p>例如: ["audit_log", "api_version", "monitoring"]
     */
    private String enabledFeatures;

    /**
     * 签名(RSA-SHA256)
     */
    private String signature;

    /**
     * 公钥指纹(用于快速验证公钥匹配)
     */
    private String publicKeyFingerprint;

    /**
     * 机器指纹（SHA-256 哈希）。
     *
     * <p>签发时由卖方写入客户机器的指纹哈希；运行时 LicenseValidator
     * 采集本机指纹并比对，不匹配则进入降级模式（基础功能放行、
     * 商业功能拒绝）。该字段参与 RSA 签名（在 signature 生成前写入），
     * 防止篡改。</p>
     *
     * <p>为 null 时跳过机器指纹验证，兼容旧版无指纹 License。</p>
     *
     * @since 1.2.1
     */
    private String machineFingerprint;

    /**
     * 是否永久有效（计算属性，不参与序列化）
     */
    @JsonIgnore
    public boolean isPermanent() {
        return validTo == null || validTo.isAfter(LocalDateTime.now().plusYears(100));
    }

    /**
     * 是否已过期（计算属性，不参与序列化）
     */
    @JsonIgnore
    public boolean isExpired() {
        return validTo != null && LocalDateTime.now().isAfter(validTo);
    }

    /**
     * 剩余天数（计算属性，不参与序列化）
     */
    @JsonIgnore
    public long getRemainingDays() {
        if (isPermanent()) {
            return Long.MAX_VALUE;
        }
        if (isExpired()) {
            return 0;
        }
        return java.time.Duration.between(LocalDateTime.now(), validTo).toDays();
    }
}
