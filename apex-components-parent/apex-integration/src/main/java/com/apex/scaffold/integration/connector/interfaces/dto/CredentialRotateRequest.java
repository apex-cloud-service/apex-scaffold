package com.apex.scaffold.integration.connector.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 轮换凭据请求（方案 M5 / 批次 E2）。
 *
 * <p>{@code secret} 为新明文，落库前加密，{@code versionNo} 递增。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
public class CredentialRotateRequest {

    /** 新明文凭据（仅入参，不回显）。 */
    @NotBlank(message = "凭据内容不能为空")
    private String secret;
}
