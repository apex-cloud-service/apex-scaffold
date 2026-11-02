package com.apex.scaffold.office.notify.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 统一通知模板持久化对象。
 *
 * <p>对应 {@code off_notify_template} 表，按（租户，模板编码，逻辑删除）唯一；
 * 正文以 {@code ${key}} 占位符声明参数，停用模板拒绝投递但保留定义。
 *
 * @author Apex Team
 * @since 1.4.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("off_notify_template")
public class NotifyTemplatePO extends BasePO {

    @TableField("tenant_id")
    private String tenantId;

    @TableField("template_code")
    private String templateCode;

    @TableField("channel")
    private String channel;

    @TableField("title")
    private String title;

    @TableField("body")
    private String body;

    @TableField("params_schema")
    private String paramsSchema;

    @TableField("status")
    private String status;
}
