package com.apex.scaffold.knowledge.domain.gateway;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.knowledge.domain.model.KbConversation;
import com.apex.scaffold.knowledge.domain.model.KbMessage;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 知识问答会话与消息仓储契约。
 *
 * @author Apex Team
 * @since 1.3.2
 */
public interface KbConversationRepository {

    /**
     * 新增会话。
     *
     * @param conversation 会话模型（含归属用户）
     * @return 已写入模型（含生成 ID）
     */
    Mono<KbConversation> insert(KbConversation conversation);

    /**
     * 更新会话标题。
     *
     * @param conversation 会话模型
     * @return 更新后的模型
     */
    Mono<KbConversation> update(KbConversation conversation);

    /**
     * 按 ID 查询会话（仅归属用户可见）。
     *
     * @param id 会话 ID
     * @param userId 归属用户 ID
     * @return 会话；不存在、跨用户或跨租户时为空
     */
    Mono<KbConversation> findByIdAndUser(Long id, Long userId);

    /**
     * 分页查询用户会话（按更新时间倒序）。
     *
     * @param userId 用户 ID
     * @param pageNum 页码
     * @param pageSize 页大小
     * @return 会话分页
     */
    Mono<PageResponse<KbConversation>> findPageByUser(Long userId, int pageNum, int pageSize);

    /**
     * 删除会话（逻辑删除，仅归属用户）。
     *
     * @param id 会话 ID
     * @param userId 用户 ID
     * @return 是否删除成功
     */
    Mono<Boolean> deleteByIdAndUser(Long id, Long userId);

    /**
     * 新增消息。
     *
     * @param message 消息模型
     * @return 已写入模型（含生成 ID）
     */
    Mono<KbMessage> insertMessage(KbMessage message);

    /**
     * 查询会话最近 N 条消息（按时间升序返回）。
     *
     * @param conversationId 会话 ID
     * @param limit 最大条数
     * @return 消息列表
     */
    Mono<List<KbMessage>> findMessages(Long conversationId, int limit);

    /**
     * 会话消息数量。
     *
     * @param conversationId 会话 ID
     * @return 数量
     */
    Mono<Long> countMessages(Long conversationId);
}
