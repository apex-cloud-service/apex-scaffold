package com.apex.scaffold.integration.connector.domain;

import java.time.LocalDateTime;

/**
 * 租户级外部系统凭据（方案 M5 / 批次 E2）。
 *
 * <p>密文与 IV 分列存储；明文仅存在于解密调用的瞬时内存，不落库、不落日志。
 * {@code versionNo} 随轮换递增，{@code rotatedAt} 记录最近一次轮换时间。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public class Credential {

    private Long id;

    private String tenantId;

    /** 凭据标识（租户内唯一，连接器配置经 credentialRef 引用）。 */
    private String credentialKey;

    /** 凭据类型（API_KEY/BASIC/TOKEN/X509）。 */
    private String credentialType;

    /** AES-256-GCM 密文（Base64，不含 IV）。 */
    private String secretCipher;

    /** 加密 IV（Base64）。 */
    private String secretIv;

    /** 凭据版本（轮换递增）。 */
    private Integer versionNo;

    /** 最近轮换时间。 */
    private LocalDateTime rotatedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getCredentialKey() {
        return credentialKey;
    }

    public void setCredentialKey(String credentialKey) {
        this.credentialKey = credentialKey;
    }

    public String getCredentialType() {
        return credentialType;
    }

    public void setCredentialType(String credentialType) {
        this.credentialType = credentialType;
    }

    public String getSecretCipher() {
        return secretCipher;
    }

    public void setSecretCipher(String secretCipher) {
        this.secretCipher = secretCipher;
    }

    public String getSecretIv() {
        return secretIv;
    }

    public void setSecretIv(String secretIv) {
        this.secretIv = secretIv;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public LocalDateTime getRotatedAt() {
        return rotatedAt;
    }

    public void setRotatedAt(LocalDateTime rotatedAt) {
        this.rotatedAt = rotatedAt;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
