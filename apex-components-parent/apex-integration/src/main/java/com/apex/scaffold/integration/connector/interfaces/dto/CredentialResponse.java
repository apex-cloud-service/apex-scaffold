package com.apex.scaffold.integration.connector.interfaces.dto;

import com.apex.scaffold.integration.connector.domain.Credential;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 凭据响应（方案 M5 / 批次 E2）。
 *
 * <p>仅含元数据：不回显明文，也<b>不回显密文与 IV</b>（避免密文被拖库后离线爆破）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
public class CredentialResponse {

    private Long id;

    private String tenantId;

    private String credentialKey;

    private String credentialType;

    /** 凭据版本（轮换递增）。 */
    private Integer versionNo;

    /** 最近轮换时间。 */
    private LocalDateTime rotatedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 领域实体转响应 DTO（裁剪密文/IV）。
     *
     * @param credential 凭据实体
     * @return 响应 DTO
     */
    public static CredentialResponse from(Credential credential) {
        CredentialResponse response = new CredentialResponse();
        response.setId(credential.getId());
        response.setTenantId(credential.getTenantId());
        response.setCredentialKey(credential.getCredentialKey());
        response.setCredentialType(credential.getCredentialType());
        response.setVersionNo(credential.getVersionNo());
        response.setRotatedAt(credential.getRotatedAt());
        response.setCreateTime(credential.getCreateTime());
        response.setUpdateTime(credential.getUpdateTime());
        return response;
    }
}
