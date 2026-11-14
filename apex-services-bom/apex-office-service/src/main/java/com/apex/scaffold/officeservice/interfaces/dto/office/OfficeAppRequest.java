package com.apex.scaffold.officeservice.interfaces.dto.office;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 办公平台应用配置请求。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "办公平台应用配置请求")
public class OfficeAppRequest {
    @NotBlank(message = "应用标识不能为空")
    @Schema(description = "平台应用标识", example = "cli_a1b2c3d4e5f6g7h8", required = true)
    private String appId;

    @NotBlank(message = "密钥环境变量引用不能为空")
    @Schema(description = "密钥环境变量引用名称", example = "FEISHU_APP_SECRET", required = true)
    private String secretReference;

    @NotBlank(message = "回调地址不能为空")
    @Schema(description = "OAuth2 回调地址", example = "https://example.com/api/v1/office/callback", required = true)
    private String redirectUri;

    @Schema(description = "签名验证密钥环境变量引用", example = "FEISHU_SIGNING_SECRET")
    private String signingSecretReference;

    @Schema(description = "平台能力配置 JSON", example = "{\"contacts\":true,\"messages\":true}")
    private String capabilities;

    @NotNull(message = "启用状态不能为空")
    @Schema(description = "是否启用", example = "true", required = true)
    private Boolean enabled;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;
}
