package com.apex.scaffold.office.notify.domain;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 通知模板实体（统一消息门面 / 批次 D1）。
 *
 * <p>按（租户，模板编码）唯一，正文以 {@code ${key}} 占位符声明参数，
 * 渲染语义见 {@link NotifyTemplateRenderer}。停用模板拒绝投递但不影响历史留痕。
 *
 * @author Apex Team
 * @version 1.4.0
 * @since 1.4.0
 */
@Getter
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class NotifyTemplate extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 启用状态：允许按模板投递。 */
    public static final String STATUS_ENABLED = "ENABLED";

    /** 停用状态：拒绝投递，仍保留模板定义与历史记录。 */
    public static final String STATUS_DISABLED = "DISABLED";

    private String tenantId;
    private String templateCode;
    private String channel;
    private String title;
    private String body;
    private String paramsSchema;
    private String status;

    /**
     * 判断模板是否处于启用状态。
     *
     * @return 启用时返回 true
     */
    public boolean isEnabled() {
        return STATUS_ENABLED.equals(status);
    }

    /**
     * 设置租户标识，赋值后刷新更新时间。
     *
     * @param tenantId 模板所属租户标识
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
        touch();
    }

    /**
     * 设置模板编码，赋值后刷新更新时间。
     *
     * @param templateCode 租户内唯一的模板编码
     */
    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
        touch();
    }

    /**
     * 设置默认投递通道，赋值后刷新更新时间。
     *
     * @param channel 通道标识（INBOX/FEISHU/DINGTALK/WECOM/SMS/EMAIL）
     */
    public void setChannel(String channel) {
        this.channel = channel;
        touch();
    }

    /**
     * 设置通知主题，赋值后刷新更新时间。
     *
     * @param title 通知主题（可空）
     */
    public void setTitle(String title) {
        this.title = title;
        touch();
    }

    /**
     * 设置正文模板，赋值后刷新更新时间。
     *
     * @param body 含 {@code ${key}} 占位符的正文模板
     */
    public void setBody(String body) {
        this.body = body;
        touch();
    }

    /**
     * 设置参数契约，赋值后刷新更新时间。
     *
     * @param paramsSchema 参数契约 JSON 字符串（供管理端提示，可空）
     */
    public void setParamsSchema(String paramsSchema) {
        this.paramsSchema = paramsSchema;
        touch();
    }

    /**
     * 设置模板状态，赋值后刷新更新时间。
     *
     * @param status {@link #STATUS_ENABLED} 或 {@link #STATUS_DISABLED}
     */
    public void setStatus(String status) {
        this.status = status;
        touch();
    }
}
