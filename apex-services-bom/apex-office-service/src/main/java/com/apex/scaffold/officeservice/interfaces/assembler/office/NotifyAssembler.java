package com.apex.scaffold.officeservice.interfaces.assembler.office;

import com.apex.scaffold.office.notify.domain.NotifyRequestRecord;
import com.apex.scaffold.office.notify.domain.NotifyTemplate;
import com.apex.scaffold.officeservice.interfaces.dto.office.notify.CreateNotifyTemplateRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.notify.NotifyRequestRecordResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.notify.NotifyTemplateResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.notify.UpdateNotifyTemplateRequest;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 统一通知 DTO 转换器
 *
 * <p>负责模板/留痕实体与接口 DTO 的双向映射；留痕的接收目标与渲染参数
 * 在存储层即为 JSON 文本，接口层原样透出，不做二次解析。
 *
 * @author Apex Scaffold Team
 * @since 1.3.2
 */
@Component
public class NotifyAssembler {

    /**
     * 将创建请求转换为模板实体（租户由应用服务按可信上下文回填）。
     *
     * @param request 创建请求
     * @return 模板实体
     */
    public NotifyTemplate toEntity(CreateNotifyTemplateRequest request) {
        NotifyTemplate entity = new NotifyTemplate();
        entity.setTemplateCode(request.getTemplateCode());
        entity.setChannel(request.getChannel());
        entity.setTitle(request.getTitle());
        entity.setBody(request.getBody());
        entity.setParamsSchema(request.getParamsSchema());
        entity.setStatus(request.getStatus());
        return entity;
    }

    /**
     * 将更新请求合并为局部变更实体；仅非空字段参与更新。
     *
     * @param request 更新请求
     * @return 仅包含变更字段的实体
     */
    public NotifyTemplate toChanges(UpdateNotifyTemplateRequest request) {
        NotifyTemplate changes = new NotifyTemplate();
        changes.setTemplateCode(request.getTemplateCode());
        changes.setChannel(request.getChannel());
        changes.setTitle(request.getTitle());
        changes.setBody(request.getBody());
        changes.setParamsSchema(request.getParamsSchema());
        changes.setStatus(request.getStatus());
        return changes;
    }

    /**
     * 将模板实体转换为响应。
     *
     * @param entity 模板实体
     * @return 模板响应
     */
    public NotifyTemplateResponse toResponse(NotifyTemplate entity) {
        return NotifyTemplateResponse.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .templateCode(entity.getTemplateCode())
                .channel(entity.getChannel())
                .title(entity.getTitle())
                .body(entity.getBody())
                .paramsSchema(entity.getParamsSchema())
                .status(entity.getStatus())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    /**
     * 将模板实体列表转换为响应列表。
     *
     * @param entities 模板实体列表
     * @return 模板响应列表
     */
    public List<NotifyTemplateResponse> toResponseList(List<NotifyTemplate> entities) {
        return entities.stream().map(this::toResponse).toList();
    }

    /**
     * 将投递留痕实体转换为响应。
     *
     * @param record 留痕实体
     * @return 留痕响应
     */
    public NotifyRequestRecordResponse toResponse(NotifyRequestRecord record) {
        return NotifyRequestRecordResponse.builder()
                .id(record.getId())
                .tenantId(record.getTenantId())
                .channel(record.getChannel())
                .templateCode(record.getTemplateCode())
                .recipient(record.getRecipient())
                .params(record.getParams())
                .status(record.getStatus())
                .errorMessage(record.getErrorMessage())
                .outboxEventId(record.getOutboxEventId())
                .createTime(record.getCreateTime())
                .updateTime(record.getUpdateTime())
                .build();
    }
}
