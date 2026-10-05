package com.apex.scaffold.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 办公消息模板持久化对象。
 *
 * <p>对应 {@code off_message_template} 表，按“租户 + 模板编码”唯一存储消息模板：
 * 模板名称、类型、内容、可用变量（JSON，经 JacksonTypeHandler 映射）与启用标志，
 * 供办公消息发送时按模板编码取模板渲染。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName(value = "off_message_template", autoResultMap = true)
public class OffMessageTemplatePO extends BasePO {

    @TableField("template_code")
    private String templateCode;

    @TableField("template_name")
    private String templateName;

    @TableField("template_type")
    private String templateType;

    @TableField("template_content")
    private String templateContent;

    @TableField(value = "variables", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> variables;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("tenant_id")
    private String tenantId;
}
