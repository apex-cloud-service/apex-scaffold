package com.apex.scaffold.integration.connector.interfaces.dto;

import com.apex.scaffold.integration.connector.domain.ConnectorDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 连接器配置响应（方案 M5 / 批次 E2）。
 *
 * <p>{@code configValue} 为 JSON 原文（敏感项以凭据引用表达，无明文凭据）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
@Schema(name = "ConnectorConfigResponse", description = "连接器配置响应；configValue 为 JSON 原文，敏感项以凭据引用表达")
public class ConnectorConfigResponse {

    @Schema(description = "配置主键 ID", example = "1", type = "integer", format = "int64")
    private Long id;

    @Schema(description = "所属租户 ID", example = "default")
    private String tenantId;

    @Schema(description = "连接器类型", example = "HTTP")
    private String connectorType;

    @Schema(description = "配置标识，租户内唯一", example = "order-sync")
    private String configKey;

    @Schema(description = "配置 JSON 原文（不含明文凭据）", example = "{\"endpoint\":\"https://api.example.com\"}")
    private String configValue;

    @Schema(
            description = "状态：0=停用，1=启用",
            example = "1",
            allowableValues = {"0", "1"},
            type = "integer",
            format = "int32")
    private Integer status;

    @Schema(description = "备注", example = "订单同步连接器")
    private String remark;

    @Schema(description = "乐观锁版本号", example = "1", type = "integer", format = "int32")
    private Integer version;

    @Schema(description = "创建时间", example = "2026-11-22T10:15:30", type = "string", format = "date-time")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2026-11-22T10:15:30", type = "string", format = "date-time")
    private LocalDateTime updateTime;

    /**
     * 领域实体转响应 DTO。
     *
     * @param definition 配置实体
     * @return 响应 DTO
     */
    public static ConnectorConfigResponse from(ConnectorDefinition definition) {
        ConnectorConfigResponse response = new ConnectorConfigResponse();
        response.setId(definition.getId());
        response.setTenantId(definition.getTenantId());
        response.setConnectorType(definition.getConnectorType());
        response.setConfigKey(definition.getConfigKey());
        response.setConfigValue(definition.getConfigValue());
        response.setStatus(definition.getStatus());
        response.setRemark(definition.getRemark());
        response.setVersion(definition.getVersion());
        response.setCreateTime(definition.getCreateTime());
        response.setUpdateTime(definition.getUpdateTime());
        return response;
    }
}
