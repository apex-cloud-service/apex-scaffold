package com.apex.scaffold.integration.connector.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登记凭据请求（方案 M5 / 批次 E2）。
 *
 * <p>{@code secret} 为明文入参，仅在本次请求内存中存在，加密落库后不落日志、
 * 任何响应不回显。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@Schema(name = "CredentialRegisterRequest", description = "登记凭据请求；secret 为明文入参，加密落库后不落日志且任何响应不回显")
public class CredentialRegisterRequest {

    /** 凭据标识（租户内唯一，连接器配置经 credentialRef 引用）。 */
    @NotBlank(message = "凭据标识不能为空")
    @Size(max = 64, message = "凭据标识长度不能超过 64")
    @Schema(
            description = "凭据标识，租户内唯一；连接器配置经 credentialRef 引用",
            example = "erp-key",
            maxLength = 64,
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String credentialKey;

    /** 凭据类型（API_KEY/BASIC/TOKEN/X509），缺省 API_KEY。 */
    @Size(max = 32, message = "凭据类型长度不能超过 32")
    @Schema(
            description = "凭据类型，缺省 API_KEY",
            example = "API_KEY",
            allowableValues = {"API_KEY", "BASIC", "TOKEN", "X509"},
            defaultValue = "API_KEY",
            maxLength = 32)
    private String credentialType;

    /** 明文凭据（仅入参，不回显）。 */
    @NotBlank(message = "凭据内容不能为空")
    @Schema(
            description = "明文凭据，仅入参不回显；服务端加密落库",
            accessMode = Schema.AccessMode.WRITE_ONLY,
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String secret;
}
