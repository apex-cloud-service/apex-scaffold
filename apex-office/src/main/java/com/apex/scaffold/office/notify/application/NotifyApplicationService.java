package com.apex.scaffold.office.notify.application;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.notify.domain.NotifyChannelPort;
import com.apex.scaffold.office.notify.domain.NotifyRequestRecord;
import com.apex.scaffold.office.notify.domain.NotifyRequestRecordRepository;
import com.apex.scaffold.office.notify.domain.NotifyTemplate;
import com.apex.scaffold.office.notify.domain.NotifyTemplateRenderer;
import com.apex.scaffold.office.notify.domain.NotifyTemplateRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 统一消息门面（方案 M4 / 批次 D1）。
 *
 * <p>把站内信、办公平台 IM、短信、邮件收敛为同一投递入口：调用方只给出
 * <b>通道 + 目标 + 模板 + 参数</b>，门面负责模板渲染、通道路由与失败收敛。
 * 通道由 {@link NotifyChannelPort} 适配器提供（新增通道免改本类）。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>通道路由</b>：请求显式 {@code channel} 优先；未指定时按模板默认通道；</li>
 *   <li><b>模板校验</b>：模板缺失参数保留占位符（不静默输出空串），并在响应中回报缺失项；</li>
 *   <li><b>失败收敛</b>：单通道投递失败不影响其它通道（多通道扇出），逐通道回报状态；</li>
 *   <li><b>租户隔离</b>：租户取自可信上下文，由通道适配器与持久化层各自落实。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyApplicationService {

    /** 通道适配器集合（按 channel() 索引）。 */
    private final List<NotifyChannelPort> channels;

    /** 通知模板仓储（按租户隔离的模板 CRUD 与编码查询）。 */
    private final NotifyTemplateRepository templateRepository;

    /** 投递留痕仓储（按次落库投递结果）。 */
    private final NotifyRequestRecordRepository recordRepository;

    /** JSON 序列化（留痕的 recipient/params 文本）。 */
    private final ObjectMapper objectMapper;

    /**
     * 单通道投递请求。
     *
     * @param channel 通道标识（INBOX / FEISHU / DINGTALK / WECOM / SMS / EMAIL）
     * @param target  目标（用户 ID / 外部 ID / 手机号 / 邮箱）
     * @param subject 主题（可空）
     * @param content 正文模板（含 {@code ${key}} 占位符）
     * @param params  渲染参数
     */
    public record NotifyRequest(
            String channel, String target, String subject, String content, Map<String, Object> params) {}

    /**
     * 单通道投递结果。
     *
     * @param channel    通道标识
     * @param delivered  是否投递成功
     * @param messageId  通道侧消息 ID（无则空串）
     * @param missingParams 模板中未提供的参数名（渲染后仍保留占位符）
     * @param error      失败原因（成功时为 null）
     */
    public record NotifyResult(
            String channel, boolean delivered, String messageId, List<String> missingParams, String error) {}

    /**
     * 投递单条通知。
     *
     * @param request 投递请求
     * @return 投递结果
     */
    public Mono<NotifyResult> send(NotifyRequest request) {
        if (request == null || request.channel() == null || request.channel().isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "通知通道不能为空"));
        }
        if (request.target() == null || request.target().isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "通知目标不能为空"));
        }
        String channelKey = request.channel().trim().toUpperCase(Locale.ROOT);
        NotifyChannelPort port = findChannel(channelKey);
        if (port == null) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "通知通道未启用或不支持: " + channelKey));
        }
        String rendered = NotifyTemplateRenderer.render(request.content(), request.params());
        List<String> missing = new ArrayList<>();
        NotifyTemplateRenderer.declaredParams(request.content()).forEach(name -> {
            if (request.params() == null || !request.params().containsKey(name)) {
                missing.add(name);
            }
        });
        return port.deliver(request.target(), request.subject(), rendered, request.params())
                .map(messageId -> new NotifyResult(channelKey, true, messageId == null ? "" : messageId, missing, null))
                .onErrorResume(error -> {
                    log.warn(
                            "通知投递失败: channel={}, target={}, reason={}",
                            channelKey,
                            request.target(),
                            error.getMessage());
                    return Mono.just(new NotifyResult(channelKey, false, "", missing, error.getMessage()));
                });
    }

    /**
     * 多通道扇出投递（同一模板投递到多个通道，逐通道独立成败）。
     *
     * @param channelTargets 通道 → 目标 的映射
     * @param subject        主题
     * @param content        正文模板
     * @param params         渲染参数
     * @return 各通道投递结果
     */
    public Flux<NotifyResult> sendFanout(
            Map<String, String> channelTargets, String subject, String content, Map<String, Object> params) {
        if (channelTargets == null || channelTargets.isEmpty()) {
            return Flux.error(new BusinessException(ResultCode.PARAM_ERROR, "至少需要一个通知通道"));
        }
        return Flux.fromIterable(channelTargets.entrySet())
                .concatMap(
                        entry -> send(new NotifyRequest(entry.getKey(), entry.getValue(), subject, content, params)));
    }

    /**
     * 按模板投递单条通知（统一消息门面收口入口）。
     *
     * <p>流程：按（租户，模板编码）加载模板（未命中报业务异常）→ 校验模板状态 →
     * 渲染并复用 {@link #send(NotifyRequest)} 投递（channel 缺省取模板默认通道）→
     * 投递结果落 {@code off_notify_request} 留痕（成功与失败均落库）。
     * 留痕失败视为整体失败向上传播，不静默吞掉。
     *
     * @param tenantId     租户标识（取自可信上下文）
     * @param templateCode 模板编码
     * @param channel      通道标识（可空，缺省用模板默认通道）
     * @param target       目标（用户 ID / 外部 ID / 手机号 / 邮箱）
     * @param params       渲染参数
     * @return 投递结果
     */
    public Mono<NotifyResult> sendByTemplate(
            String tenantId, String templateCode, String channel, String target, Map<String, Object> params) {
        if (tenantId == null || tenantId.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "租户不能为空"));
        }
        if (templateCode == null || templateCode.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "模板编码不能为空"));
        }
        if (target == null || target.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "通知目标不能为空"));
        }
        return templateRepository
                .findByCode(tenantId, templateCode)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "通知模板不存在: " + templateCode)))
                .flatMap(template -> {
                    if (!template.isEnabled()) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "通知模板已停用: " + templateCode));
                    }
                    String channelKey = channel == null || channel.isBlank() ? template.getChannel() : channel;
                    // 标题与正文均按模板渲染占位符；send() 只渲染正文，主题在进入前完成渲染
                    String subject = template.getTitle() == null
                            ? null
                            : NotifyTemplateRenderer.render(template.getTitle(), params);
                    return send(new NotifyRequest(channelKey, target, subject, template.getBody(), params))
                            .flatMap(result -> recordDelivery(tenantId, templateCode, target, params, result)
                                    .thenReturn(result));
                });
    }

    /**
     * 创建通知模板。
     *
     * @param tenantId 租户标识
     * @param entity   模板实体（租户以入参为准）
     * @return 创建后的模板（含主键）
     */
    public Mono<NotifyTemplate> createTemplate(String tenantId, NotifyTemplate entity) {
        if (tenantId == null || tenantId.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "租户不能为空"));
        }
        if (entity.getTemplateCode() == null || entity.getTemplateCode().isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "模板编码不能为空"));
        }
        if (entity.getChannel() == null || entity.getChannel().isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "通知通道不能为空"));
        }
        entity.setTenantId(tenantId);
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus(NotifyTemplate.STATUS_ENABLED);
        }
        // 后续步骤统一 defer，避免前置校验失败时仍触发下游写入调用
        return templateRepository
                .findByCode(tenantId, entity.getTemplateCode())
                .flatMap(existing -> Mono.<NotifyTemplate>error(
                        new BusinessException(ResultCode.PARAM_ERROR, "模板编码已存在: " + entity.getTemplateCode())))
                .switchIfEmpty(Mono.defer(() -> templateRepository.insert(entity)))
                .doOnSuccess(saved ->
                        log.info("通知模板创建成功 - tenantId: {}, templateCode: {}", tenantId, saved.getTemplateCode()));
    }

    /**
     * 更新通知模板（局部变更，仅非空字段生效）。
     *
     * @param tenantId 租户标识
     * @param id       模板主键
     * @param changes  变更数据
     * @return 更新后的模板
     */
    public Mono<NotifyTemplate> updateTemplate(String tenantId, Long id, NotifyTemplate changes) {
        return requireTemplate(tenantId, id)
                .flatMap(existing -> {
                    if (changes.getTemplateCode() != null
                            && !changes.getTemplateCode().equals(existing.getTemplateCode())) {
                        return templateRepository
                                .findByCode(tenantId, changes.getTemplateCode())
                                .flatMap(conflict -> Mono.<NotifyTemplate>error(new BusinessException(
                                        ResultCode.PARAM_ERROR, "模板编码已存在: " + changes.getTemplateCode())))
                                .switchIfEmpty(Mono.just(existing));
                    }
                    return Mono.just(existing);
                })
                .flatMap(existing -> {
                    applyTemplateChanges(existing, changes);
                    return templateRepository.update(existing);
                })
                .doOnSuccess(updated -> log.info("通知模板更新成功 - tenantId: {}, templateId: {}", tenantId, id));
    }

    /**
     * 删除通知模板（逻辑删除），历史投递留痕保留。
     *
     * @param tenantId 租户标识
     * @param id       模板主键
     * @return 是否删除成功
     */
    public Mono<Boolean> deleteTemplate(String tenantId, Long id) {
        return requireTemplate(tenantId, id)
                .flatMap(template -> templateRepository.deleteById(tenantId, id))
                .doOnSuccess(deleted ->
                        log.info("通知模板删除 - tenantId: {}, templateId: {}, deleted: {}", tenantId, id, deleted));
    }

    /**
     * 查询通知模板详情。
     *
     * @param tenantId 租户标识
     * @param id       模板主键
     * @return 模板详情
     */
    public Mono<NotifyTemplate> getTemplate(String tenantId, Long id) {
        return requireTemplate(tenantId, id);
    }

    /**
     * 分页查询通知模板。
     *
     * @param tenantId 租户标识
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页大小
     * @param channel  通道过滤（可空）
     * @param keyword  编码或主题模糊匹配（可空）
     * @return 模板分页结果
     */
    public Mono<PageResponse<NotifyTemplate>> listTemplates(
            String tenantId, Integer pageNum, Integer pageSize, String channel, String keyword) {
        return templateRepository.findPage(tenantId, pageNum, pageSize, channel, keyword);
    }

    /**
     * 分页查询投递留痕。
     *
     * @param tenantId     租户标识
     * @param pageNum      页码，从 1 开始
     * @param pageSize     每页大小
     * @param channel      通道过滤（可空）
     * @param templateCode 模板编码过滤（可空）
     * @param status       投递状态过滤（可空）
     * @return 留痕分页结果
     */
    public Mono<PageResponse<NotifyRequestRecord>> listRecords(
            String tenantId, Integer pageNum, Integer pageSize, String channel, String templateCode, String status) {
        return recordRepository.findPage(tenantId, pageNum, pageSize, channel, templateCode, status);
    }

    /**
     * 加载租户内模板，未命中报业务异常。
     */
    private Mono<NotifyTemplate> requireTemplate(String tenantId, Long id) {
        if (tenantId == null || tenantId.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "租户不能为空"));
        }
        return templateRepository
                .findById(tenantId, id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "通知模板不存在")));
    }

    /**
     * 将局部变更合并到既有模板（仅非空字段生效）。
     */
    private void applyTemplateChanges(NotifyTemplate existing, NotifyTemplate changes) {
        if (changes.getChannel() != null && !changes.getChannel().isBlank()) {
            existing.setChannel(changes.getChannel());
        }
        if (changes.getTitle() != null) {
            existing.setTitle(changes.getTitle());
        }
        if (changes.getBody() != null) {
            existing.setBody(changes.getBody());
        }
        if (changes.getParamsSchema() != null) {
            existing.setParamsSchema(changes.getParamsSchema());
        }
        if (changes.getStatus() != null && !changes.getStatus().isBlank()) {
            existing.setStatus(changes.getStatus());
        }
    }

    /**
     * 投递结果落留痕表：成功与失败均按次记录，失败原因原样保存。
     */
    private Mono<NotifyRequestRecord> recordDelivery(
            String tenantId, String templateCode, String target, Map<String, Object> params, NotifyResult result) {
        NotifyRequestRecord record = new NotifyRequestRecord();
        record.setTenantId(tenantId);
        record.setChannel(result.channel());
        record.setTemplateCode(templateCode);
        record.setRecipient(writeJson(List.of(target), target));
        record.setParams(writeJson(params, String.valueOf(params)));
        record.setStatus(result.delivered() ? NotifyRequestRecord.STATUS_SUCCESS : NotifyRequestRecord.STATUS_FAILED);
        record.setErrorMessage(result.error());
        return recordRepository.insert(record);
    }

    /**
     * 序列化留痕 JSON 文本；序列化失败时回退为原始字符串并告警，避免丢失留痕。
     */
    private String writeJson(Object value, String fallback) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("通知留痕 JSON 序列化失败，按原始字符串落库: {}", e.getMessage());
            return fallback;
        }
    }

    /**
     * 已启用的通道标识（运维/管理端展示）。
     *
     * @return 通道标识列表
     */
    public List<String> availableChannels() {
        return channels == null
                ? List.of()
                : channels.stream().map(NotifyChannelPort::channel).distinct().toList();
    }

    /**
     * 按通道标识查找适配器（大小写不敏感）。
     *
     * @param channelKey 已大写化的通道标识
     * @return 适配器；未装配时为 null
     */
    private NotifyChannelPort findChannel(String channelKey) {
        if (channels == null) {
            return null;
        }
        return channels.stream()
                .filter(port -> channelKey.equalsIgnoreCase(port.channel()))
                .findFirst()
                .orElse(null);
    }
}
