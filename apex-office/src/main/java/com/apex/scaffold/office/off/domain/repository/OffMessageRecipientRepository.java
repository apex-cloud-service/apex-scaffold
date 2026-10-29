package com.apex.scaffold.office.off.domain.repository;

import com.apex.scaffold.office.off.domain.entity.OffMessageRecipientEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 办公消息接收人仓储接口
 *
 * <p>定义办公平台消息接收人关系的持久化操作契约，管理消息与接收人的多对多关系。
 * 支持消息接收记录的增删改查、已读/未读状态管理、批量操作等功能。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>消息接收记录的基础 CRUD 操作</li>
 *   <li>按消息ID查询所有接收人</li>
 *   <li>按接收人ID查询收到的消息</li>
 *   <li>查询特定消息和接收人的关系记录</li>
 *   <li>批量插入接收人记录（群发消息）</li>
 *   <li>标记消息为已读（单条/批量）</li>
 *   <li>统计未读消息数量</li>
 * </ul>
 *
 * <h2>查询场景</h2>
 * <ul>
 *   <li>消息详情：查看某消息的所有接收人及阅读状态</li>
 *   <li>收件箱：查询用户收到的所有消息</li>
 *   <li>已读处理：标记单条或全部消息为已读</li>
 *   <li>未读统计：显示用户的未读消息数量</li>
 * </ul>
 *
 * <h2>技术特性</h2>
 * <ul>
 *   <li>响应式编程：所有方法返回 Mono/Flux</li>
 *   <li>关系管理：消息与接收人的多对多关系表</li>
 *   <li>批量操作：支持批量插入提高群发消息性能</li>
 *   <li>状态跟踪：记录每个接收人的阅读状态和时间</li>
 *   <li>快速统计：提供高效的未读数量统计</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 * @see OffMessageRecipientEntity
 */
public interface OffMessageRecipientRepository {

    /**
     * 根据ID查询消息接收记录
     *
     * @param id 记录ID
     * @return 消息接收记录的Mono包装，如果不存在则返回空Mono
     */
    Mono<OffMessageRecipientEntity> findById(Long id);

    /**
     * 根据消息ID查询所有接收人记录
     *
     * @param messageId 消息ID
     * @return 消息接收记录列表的Flux流
     */
    Flux<OffMessageRecipientEntity> findByMessageId(Long messageId);

    /**
     * 查询接收人收到的所有消息记录
     *
     * @param recipientId 接收人ID
     * @return 消息接收记录列表的Flux流
     */
    Flux<OffMessageRecipientEntity> findByRecipientId(Long recipientId);

    /**
     * 查询特定消息和接收人的关系记录
     *
     * @param messageId 消息ID
     * @param recipientId 接收人ID
     * @return 消息接收记录的Mono包装，如果不存在则返回空Mono
     */
    Mono<OffMessageRecipientEntity> findByMessageIdAndRecipientId(Long messageId, Long recipientId);

    /**
     * 插入新的消息接收记录
     *
     * @param entity 消息接收记录实体
     * @return 受影响的行数
     */
    Mono<Integer> insert(OffMessageRecipientEntity entity);

    /**
     * 批量插入消息接收记录，用于群发消息
     *
     * @param entities 消息接收记录实体列表
     * @return 受影响的总行数
     */
    Mono<Integer> batchInsert(java.util.List<OffMessageRecipientEntity> entities);

    /**
     * 更新消息接收记录
     *
     * @param entity 消息接收记录实体，必须包含ID
     * @return 受影响的行数
     */
    Mono<Integer> update(OffMessageRecipientEntity entity);

    /**
     * 根据ID删除消息接收记录
     *
     * @param id 记录ID
     * @return 删除操作的Mono包装
     */
    Mono<Void> deleteById(Long id);

    /**
     * 统计接收人的未读消息数量
     *
     * @param recipientId 接收人ID
     * @return 未读消息数量
     */
    Mono<Long> countUnreadByRecipientId(Long recipientId);

    /**
     * 标记指定消息为已读
     *
     * @param messageId 消息ID
     * @param recipientId 接收人ID
     * @return 受影响的行数
     */
    Mono<Integer> markAsRead(Long messageId, Long recipientId);

    /**
     * 标记所有未读消息为已读
     *
     * @param recipientId 接收人ID
     * @return 更新的记录数
     */
    Mono<Integer> markAllAsRead(Long recipientId);

    /**
     * 增量查询收件箱（游标分页）
     *
     * @param recipientId 接收人ID
     * @param afterId 游标：查询 id > afterId 的记录，首次传 0
     * @param limit 返回记录数上限
     * @return 按 id 升序的接收记录列表
     */
    Flux<OffMessageRecipientEntity> findInboxAfterId(Long recipientId, Long afterId, int limit);
}
