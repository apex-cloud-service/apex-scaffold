package com.apex.scaffold.integration.connector.interfaces.dto;

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
public class CredentialRegisterRequest {

    /** 凭据标识（租户内唯一，连接器配置经 credentialRef 引用）。 */
    @NotBlank(message = "凭据标识不能为空")
    @Size(max = 64, message = "凭据标识长度不能超过 64")
    private String credentialKey;

    /** 凭据类型（API_KEY/BASIC/TOKEN/X509），缺省 API_KEY。 */
    @Size(max = 32, message = "凭据类型长度不能超过 32")
    private String credentialType;

    /** 明文凭据（仅入参，不回显）。 */
    @NotBlank(message = "凭据内容不能为空")
    private String secret;
}
