package com.apex.scaffold.domain.model.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 代码生成器模板实体
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class GenTemplateEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String tenantId;
    private String templateName;
    private String templateType;
    private String templateContent;
    private String outputFileName;
    private String outputSubPath;
    private String remark;

    public GenTemplateEntity() {
        super();
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

    /**
     * 设置模板名称，并刷新实体更新时间。
     *
     * @param templateName 代码生成模板的显示名称
     */
    public void setTemplateName(String templateName) {
        this.templateName = templateName;
        touch();
    }

    /**
     * 设置模板类型，并刷新实体更新时间。
     *
     * @param templateType 模板的分类类型标识（如 Entity、Repository、Service）
     */
    public void setTemplateType(String templateType) {
        this.templateType = templateType;
        touch();
    }

    /**
     * 设置模板内容，并刷新实体更新时间。
     *
     * @param templateContent 模板的正文内容，可包含代码生成占位符
     */
    public void setTemplateContent(String templateContent) {
        this.templateContent = templateContent;
        touch();
    }

    /**
     * 设置输出文件名，并刷新实体更新时间。
     *
     * @param outputFileName 使用该模板生成的目标文件名
     */
    public void setOutputFileName(String outputFileName) {
        this.outputFileName = outputFileName;
        touch();
    }

    /**
     * 设置输出子路径，并刷新实体更新时间。
     *
     * @param outputSubPath 生成文件相对于输出根目录的子目录路径
     */
    public void setOutputSubPath(String outputSubPath) {
        this.outputSubPath = outputSubPath;
        touch();
    }

    /**
     * 设置模板备注信息，并刷新实体更新时间。
     *
     * @param remark 对该模板的补充说明，可为空
     */
    public void setRemark(String remark) {
        this.remark = remark;
        touch();
    }
}
