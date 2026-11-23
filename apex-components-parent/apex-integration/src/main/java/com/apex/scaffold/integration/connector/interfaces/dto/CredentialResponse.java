package com.apex.scaffold.integration.connector.interfaces.dto;

import com.apex.scaffold.integration.connector.domain.Credential;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(name = "CredentialResponse", description = "凭据响应；仅含元数据，不回显明文、密文与 IV")
public class CredentialResponse {

    @Schema(description = "凭据主键 ID", example = "1", type = "integer", format = "int64")
    private Long id;

    @Schema(description = "所属租户 ID", example = "default")
    private String tenantId;

    @Schema(description = "凭据标识", example = "erp-key")
    private String credentialKey;

    @Schema(
            description = "凭据类型",
            example = "API_KEY",
            allowableValues = {"API_KEY", "BASIC", "TOKEN", "X509"})
    private String credentialType;

    /** 凭据版本（轮换递增）。 */
    @Schema(description = "凭据版本，每次轮换递增", example = "2", type = "integer", format = "int32")
    private Integer versionNo;

    /** 最近轮换时间。 */
    @Schema(description = "最近轮换时间；从未轮换时为 null", example = "2026-11-22T10:15:30", type = "string", format = "date-time")
    private LocalDateTime rotatedAt;

    @Schema(description = "创建时间", example = "2026-11-22T10:15:30", type = "string", format = "date-time")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2026-11-22T10:15:30", type = "string", format = "date-time")
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
