package com.apex.scaffold.integration.connector.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新连接器配置请求（方案 M5 / 批次 E2）。
 *
 * <p>字段为 null 表示不修改；{@code version} 为乐观锁版本，必填。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@Schema(name = "ConnectorConfigUpdateRequest", description = "更新连接器配置请求；字段为 null 表示不修改，version 为乐观锁版本且必填")
public class ConnectorConfigUpdateRequest {

    /** 配置 JSON（null 表示不修改）。 */
    @Size(min = 1, message = "配置 JSON 不能为空白")
    @Schema(
            description = "配置 JSON 字符串；为 null 表示不修改",
            example = "{\"endpoint\":\"https://api.example.com\",\"timeoutMs\":5000}")
    private String configValue;

    /** 备注（null 表示不修改）。 */
    @Size(max = 255, message = "备注长度不能超过 255")
    @Schema(description = "备注；为 null 表示不修改", example = "订单同步连接器（已调大超时）", maxLength = 255)
    private String remark;

    /** 乐观锁版本。 */
    @NotNull(message = "版本号不能为空")
    @Schema(
            description = "乐观锁版本号，必须与当前记录一致，否则更新失败",
            example = "1",
            type = "integer",
            format = "int32",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer version;
}
