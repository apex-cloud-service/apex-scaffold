package com.apex.scaffold.interfaces.dto.office;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 办公平台应用配置响应。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "办公平台应用配置响应")
public class OfficeAppResponse {
    @Schema(description = "主键", example = "1001")
    private Long id;

    @Schema(description = "租户标识", example = "tenant-a")
    private String tenantId;

    @Schema(description = "平台类型", example = "FEISHU")
    private String platform;

    @Schema(description = "平台应用标识", example = "cli_a1b2c3d4e5f6g7h8")
    private String appId;

    @Schema(description = "OAuth2 回调地址", example = "https://example.com/api/v1/office/callback")
    private String redirectUri;

    @Schema(description = "平台能力配置 JSON", example = "{\"contacts\":true,\"messages\":true}")
    private String capabilities;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    @Schema(description = "乐观锁版本号", example = "0")
    private Integer version;
}
