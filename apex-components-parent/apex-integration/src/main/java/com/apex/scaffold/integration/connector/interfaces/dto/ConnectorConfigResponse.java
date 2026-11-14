package com.apex.scaffold.integration.connector.interfaces.dto;

import com.apex.scaffold.integration.connector.domain.ConnectorDefinition;
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
public class ConnectorConfigResponse {

    private Long id;

    private String tenantId;

    private String connectorType;

    private String configKey;

    private String configValue;

    private Integer status;

    private String remark;

    private Integer version;

    private LocalDateTime createTime;

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
