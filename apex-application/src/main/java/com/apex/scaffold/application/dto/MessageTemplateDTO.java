package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息模板 DTO。
 *
 * <p>办公消息业务域的消息模板数据载体，承载模板编码、名称、类型、内容、
 * 变量定义与启用状态等信息，用于 {@code OffMessageTemplateService}
 * 消息模板管理用例中实体与服务层之间的数据传输。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "消息模板DTO")
public class MessageTemplateDTO {

    @Schema(description = "模板ID")
    private Long id;

    @Schema(description = "模板编码")
    private String templateCode;

    @Schema(description = "模板名称")
    private String templateName;

    @Schema(description = "模板类型")
    private String templateType;

    @Schema(description = "模板内容")
    private String templateContent;

    @Schema(description = "变量定义")
    private Map<String, Object> variables;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
