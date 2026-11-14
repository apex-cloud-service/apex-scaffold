package com.apex.scaffold.knowledge.infrastructure.persistence.repository;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.knowledge.domain.gateway.KbConversationRepository;
import com.apex.scaffold.knowledge.domain.model.KbConversation;
import com.apex.scaffold.knowledge.domain.model.KbMessage;
import com.apex.scaffold.knowledge.infrastructure.persistence.mapper.KbConversationMapper;
import com.apex.scaffold.knowledge.infrastructure.persistence.mapper.KbMessageMapper;
import com.apex.scaffold.knowledge.infrastructure.persistence.po.KbConversationPO;
import com.apex.scaffold.knowledge.infrastructure.persistence.po.KbMessagePO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 知识问答会话与消息仓储实现：MyBatis-Plus 访问统一隔离到 jdbc 调度器。
 *
 * <p>会话与消息的归属（用户 + 租户）在查询条件中显式限定；引用溯源 JSON
 * 解析失败按空引用降级并记录告警，不阻塞会话读取。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class KbConversationRepositoryImpl implements KbConversationRepository {

    private final KbConversationMapper conversationMapper;

    private final KbMessageMapper messageMapper;

    private final ObjectMapper objectMapper;

    @Override
    public Mono<KbConversation> insert(KbConversation conversation) {
        return Mono.fromCallable(() -> {
                    KbConversationPO po = KbConversationPO.builder()
                            .title(conversation.getTitle())
                            .userId(conversation.getUserId())
                            .tenantId(TenantContext.currentTenantId().orElse(null))
                            .build();
                    conversationMapper.insert(po);
                    return toModel(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<KbConversation> update(KbConversation conversation) {
        return Mono.fromCallable(() -> {
                    KbConversationPO po = conversationMapper.selectById(conversation.getId());
                    if (po == null) {
                        throw new IllegalStateException("会话不存在: " + conversation.getId());
                    }
                    po.setTitle(conversation.getTitle());
                    conversationMapper.updateById(po);
                    return toModel(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<KbConversation> findByIdAndUser(Long id, Long userId) {
        return Mono.fromCallable(() -> {
                    KbConversationPO po = conversationMapper.selectOne(new LambdaQueryWrapper<KbConversationPO>()
                            .eq(KbConversationPO::getId, id)
                            .eq(KbConversationPO::getUserId, userId));
                    return po == null ? null : toModel(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<PageResponse<KbConversation>> findPageByUser(Long userId, int pageNum, int pageSize) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<KbConversationPO> wrapper = new LambdaQueryWrapper<KbConversationPO>()
                            .eq(KbConversationPO::getUserId, userId)
                            .orderByDesc(KbConversationPO::getUpdateTime);
                    Page<KbConversationPO> page = conversationMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
                    List<KbConversation> items =
                            page.getRecords().stream().map(this::toModel).toList();
                    return PageResponse.of(items, page.getTotal(), pageNum, pageSize);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Boolean> deleteByIdAndUser(Long id, Long userId) {
        return Mono.fromCallable(() -> conversationMapper.delete(new LambdaQueryWrapper<KbConversationPO>()
                                .eq(KbConversationPO::getId, id)
                                .eq(KbConversationPO::getUserId, userId))
                        > 0)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<KbMessage> insertMessage(KbMessage message) {
        return Mono.fromCallable(() -> {
                    KbMessagePO po = KbMessagePO.builder()
                            .conversationId(message.getConversationId())
                            .role(message.getRole().name())
                            .content(message.getContent())
                            .citations(writeCitations(message.getCitations()))
                            .tenantId(TenantContext.currentTenantId().orElse(null))
                            .build();
                    messageMapper.insert(po);
                    return toMessageModel(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<List<KbMessage>> findMessages(Long conversationId, int limit) {
        return Mono.fromCallable(() -> {
                    // 倒序取最近 N 条后反转为时间升序，保证历史上下文语义
                    List<KbMessage> latest = messageMapper
                            .selectList(new LambdaQueryWrapper<KbMessagePO>()
                                    .eq(KbMessagePO::getConversationId, conversationId)
                                    .orderByDesc(KbMessagePO::getId)
                                    .last("LIMIT " + Math.max(1, limit)))
                            .stream()
                            .map(this::toMessageModel)
                            .toList();
                    return latest.reversed();
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Long> countMessages(Long conversationId) {
        return Mono.fromCallable(() -> messageMapper.selectCount(
                        new LambdaQueryWrapper<KbMessagePO>().eq(KbMessagePO::getConversationId, conversationId)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    private String writeCitations(List<KbMessage.Citation> citations) {
        if (citations == null || citations.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (Exception e) {
            log.warn("引用溯源序列化失败，按空引用存储", e);
            return null;
        }
    }

    private List<KbMessage.Citation> readCitations(String citations) {
        if (citations == null || citations.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(citations, new TypeReference<List<KbMessage.Citation>>() {});
        } catch (Exception e) {
            log.warn("引用溯源反序列化失败，按空引用返回", e);
            return List.of();
        }
    }

    private KbConversation toModel(KbConversationPO po) {
        return KbConversation.builder()
                .id(po.getId())
                .title(po.getTitle())
                .userId(po.getUserId())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    private KbMessage toMessageModel(KbMessagePO po) {
        return KbMessage.builder()
                .id(po.getId())
                .conversationId(po.getConversationId())
                .role(KbMessage.Role.valueOf(po.getRole()))
                .content(po.getContent())
                .citations(readCitations(po.getCitations()))
                .createTime(po.getCreateTime())
                .build();
    }
}
