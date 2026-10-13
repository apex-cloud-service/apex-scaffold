package com.apex.scaffold.office.off.domain.entity;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 消息模板实体
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class OffMessageTemplateEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String templateCode;
    private String templateName;
    private String templateType;
    private String templateContent;
    private Map<String, Object> variables;
    private Boolean enabled;
    private String tenantId;

    public OffMessageTemplateEntity() {
        super();
    }

    /**
     * 设置模板编码，并刷新实体更新时间。
     *
     * @param templateCode 消息模板的唯一业务编码
     */
    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
        touch();
    }

    /**
     * 设置模板名称，并刷新实体更新时间。
     *
     * @param templateName 消息模板的显示名称
     */
    public void setTemplateName(String templateName) {
        this.templateName = templateName;
        touch();
    }

    /**
     * 设置模板类型，并刷新实体更新时间。
     *
     * @param templateType 消息模板的分类类型标识
     */
    public void setTemplateType(String templateType) {
        this.templateType = templateType;
        touch();
    }

    /**
     * 设置模板内容，并刷新实体更新时间。
     *
     * @param templateContent 消息模板的正文内容，可包含变量占位符
     */
    public void setTemplateContent(String templateContent) {
        this.templateContent = templateContent;
        touch();
    }

    /**
     * 设置模板变量定义，并刷新实体更新时间。
     *
     * @param variables 模板中可用的变量名与默认值映射
     */
    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
        touch();
    }

    /**
     * 设置模板启用状态，并刷新实体更新时间。
     *
     * @param enabled true-启用该模板，false-停用该模板
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
        touch();
    }

    /**
     * 设置租户 ID，并刷新实体更新时间。
     *
     * @param tenantId 模板所属租户的唯一标识
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
        touch();
    }
}
