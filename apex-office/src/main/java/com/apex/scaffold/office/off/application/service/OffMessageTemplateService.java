package com.apex.scaffold.office.off.application.service;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.office.off.application.assembler.OffMessageTemplateAssembler;
import com.apex.scaffold.office.off.application.dto.MessageTemplateDTO;
import com.apex.scaffold.office.off.domain.entity.OffMessageTemplateEntity;
import com.apex.scaffold.office.off.domain.repository.OffMessageTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 消息模板应用服务
 *
 * <p>提供消息模板的创建、查询、更新、删除等管理功能。
 * 支持按模板编码、模板类型查询模板，以及获取所有启用的模板列表。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>模板查询：按模板编码或模板类型查询模板</li>
 *   <li>模板列表：获取所有启用的模板列表</li>
 *   <li>模板创建：创建新的消息模板，自动关联租户</li>
 *   <li>模板更新：更新模板名称、内容、变量和启用状态</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>模板内容支持占位符变量，发送消息时动态替换为实际值。
 * 例如：{@code "尊敬的{userName}，您的订单{orderId}已发货"}
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>使用 Reactor 响应式编程模型，返回 Mono/Flux</li>
 *   <li>通过 Assembler 实现 DTO 与 Entity 之间的转换</li>
 *   <li>租户ID从 TenantContext 自动获取并注入</li>
 *   <li>新建模板默认为启用状态</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 * @see OffMessageTemplateRepository
 * @see OffMessageTemplateAssembler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OffMessageTemplateService {

    private final OffMessageTemplateRepository templateRepository;
    private final OffMessageTemplateAssembler assembler;

    /**
     * 按模板编码查询消息模板
     *
     * @param templateCode 模板编码
     * @return 消息模板 DTO；模板不存在时 Mono 为空
     */
    public Mono<MessageTemplateDTO> getTemplateByCode(String templateCode) {
        return templateRepository.findByTemplateCode(templateCode).map(assembler::toDTO);
    }

    /**
     * 按模板类型查询消息模板列表
     *
     * @param templateType 模板类型
     * @return 该类型下的消息模板 DTO 流
     */
    public Flux<MessageTemplateDTO> getTemplatesByType(String templateType) {
        return templateRepository.findByTemplateType(templateType).map(assembler::toDTO);
    }

    /**
     * 查询所有启用的消息模板
     *
     * @return 已启用模板的 DTO 流
     */
    public Flux<MessageTemplateDTO> getAllEnabledTemplates() {
        return templateRepository.findAllEnabled().map(assembler::toDTO);
    }

    /**
     * 创建消息模板
     *
     * <p>自动注入当前租户 ID，并将模板设置为启用状态。
     *
     * @param dto 消息模板 DTO（含模板编码、名称、内容、变量等）
     * @return 创建后的消息模板 DTO
     */
    public Mono<MessageTemplateDTO> createTemplate(MessageTemplateDTO dto) {
        OffMessageTemplateEntity entity = assembler.toEntity(dto);
        entity.setTenantId(TenantContext.currentTenantId().orElse(null));
        entity.setEnabled(true);

        return templateRepository
                .insert(entity)
                .flatMap(count -> templateRepository.findById(entity.getId()))
                .map(assembler::toDTO);
    }

    /**
     * 更新消息模板
     *
     * <p>更新模板名称、内容、变量与启用状态。
     *
     * @param id 模板 ID
     * @param dto 消息模板 DTO（含待更新的字段值）
     * @return 更新后的消息模板 DTO；模板不存在时 Mono 为空
     */
    public Mono<MessageTemplateDTO> updateTemplate(Long id, MessageTemplateDTO dto) {
        return templateRepository
                .findById(id)
                .flatMap(existing -> {
                    existing.setTemplateName(dto.getTemplateName());
                    existing.setTemplateContent(dto.getTemplateContent());
                    existing.setVariables(dto.getVariables());
                    existing.setEnabled(dto.getEnabled());
                    return templateRepository.update(existing);
                })
                .flatMap(count -> templateRepository.findById(id))
                .map(assembler::toDTO);
    }

    /**
     * 删除消息模板
     *
     * @param id 模板 ID
     */
    public Mono<Void> deleteTemplate(Long id) {
        return templateRepository.deleteById(id);
    }

    /**
     * 分页查询消息模板
     *
     * <p>支持按模板类型与启用状态过滤。
     *
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页条数
     * @param templateType 模板类型，可为 null 表示不过滤
     * @param enabled 启用状态，可为 null 表示不过滤
     * @return 分页结果（含总条数与当前页模板列表）
     */
    public Mono<PageResponse<MessageTemplateDTO>> getTemplatePage(
            Integer pageNum, Integer pageSize, String templateType, Boolean enabled) {
        return templateRepository
                .findPage(pageNum, pageSize, templateType, enabled)
                .map(page -> {
                    PageResponse<MessageTemplateDTO> response = new PageResponse<>();
                    response.setTotal(page.getTotal());
                    response.setRecords(assembler.toDTOList(page.getRecords()));
                    return response;
                });
    }
}
