package com.apex.scaffold.integration.connector.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增连接器配置请求（方案 M5 / 批次 E2）。
 *
 * <p>{@code configValue} 为 JSON 文本；敏感值以凭据引用表达，不接收明文凭据。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Data
public class ConnectorConfigCreateRequest {

    /** 连接器类型（HTTP/WEBHOOK/WECHAT/CHUANGLAN 或第三方 SPI 类型）。 */
    @NotBlank(message = "连接器类型不能为空")
    @Size(max = 64, message = "连接器类型长度不能超过 64")
    private String connectorType;

    /** 配置标识（租户内唯一）。 */
    @NotBlank(message = "配置标识不能为空")
    @Size(max = 64, message = "配置标识长度不能超过 64")
    private String configKey;

    /** 配置 JSON（端点、超时、凭据引用等）。 */
    @NotBlank(message = "配置 JSON 不能为空")
    private String configValue;

    /** 备注（可空）。 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
