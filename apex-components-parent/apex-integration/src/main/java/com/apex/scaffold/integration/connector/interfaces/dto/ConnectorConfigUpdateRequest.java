package com.apex.scaffold.integration.connector.interfaces.dto;

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
public class ConnectorConfigUpdateRequest {

    /** 配置 JSON（null 表示不修改）。 */
    @Size(min = 1, message = "配置 JSON 不能为空白")
    private String configValue;

    /** 备注（null 表示不修改）。 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    /** 乐观锁版本。 */
    @NotNull(message = "版本号不能为空")
    private Integer version;
}
