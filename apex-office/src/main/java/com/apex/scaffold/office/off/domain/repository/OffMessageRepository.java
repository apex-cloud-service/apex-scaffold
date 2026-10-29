package com.apex.scaffold.office.off.domain.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.office.off.domain.entity.OffMessageEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 办公消息仓储接口
 *
 * <p>定义办公平台消息的持久化操作契约，支持消息的增删改查、收发件箱查询、未读统计、状态管理等功能。
 * 用于站内消息、系统通知、业务提醒等场景。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>消息的基础 CRUD 操作</li>
 *   <li>按接收人查询消息（收件箱）</li>
 *   <li>按发送人查询消息（发件箱）</li>
 *   <li>统计未读消息数量</li>
 *   <li>按消息状态和类型筛选</li>
 *   <li>分页查询和消息编码查询</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>收件箱：查询用户收到的消息列表</li>
 *   <li>发件箱：查询用户发送的消息列表</li>
 *   <li>未读提醒：统计和显示未读消息数量</li>
 *   <li>消息管理：分页查询、状态筛选、类型筛选</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>响应式编程：所有方法返回 Mono/Flux</li>
 *   <li>消息编码：messageCode 作为业务唯一标识</li>
 *   <li>收发分离：支持按接收人和发送人分别查询</li>
 *   <li>未读统计：提供高效的未读数量统计</li>
 *   <li>分页支持：支持大量消息的分页加载</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 * @see OffMessageEntity
 */
public interface OffMessageRepository {

    /**
     * 根据消息ID查询消息信息
     *
     * @param id 消息ID
     * @return 消息实体的Mono包装，如果不存在则返回空Mono
     */
    Mono<OffMessageEntity> findById(Long id);

    /**
     * 根据消息编码查询消息信息
     *
     * @param messageCode 消息编码，唯一标识
     * @return 消息实体的Mono包装，如果不存在则返回空Mono
     */
    Mono<OffMessageEntity> findByMessageCode(String messageCode);

    /**
     * 查询接收人收到的消息列表（收件箱）
     *
     * @param recipientId 接收人ID
     * @param pageNum 页码，从1开始
     * @param pageSize 每页大小
     * @return 消息列表的Flux流
     */
    Flux<OffMessageEntity> findByRecipientId(Long recipientId, Integer pageNum, Integer pageSize);

    /**
     * 查询发送人发送的消息列表（发件箱）
     *
     * @param senderId 发送人ID
     * @param pageNum 页码，从1开始
     * @param pageSize 每页大小
     * @return 消息列表的Flux流
     */
    Flux<OffMessageEntity> findBySenderId(Long senderId, Integer pageNum, Integer pageSize);

    /**
     * 插入新消息记录
     *
     * @param entity 消息实体
     * @return 受影响的行数
     */
    Mono<Integer> insert(OffMessageEntity entity);

    /**
     * 更新消息信息
     *
     * @param entity 消息实体，必须包含ID
     * @return 受影响的行数
     */
    Mono<Integer> update(OffMessageEntity entity);

    /**
     * 根据ID删除消息（逻辑删除）
     *
     * @param id 消息ID
     */
    Mono<Void> deleteById(Long id);

    /**
     * 统计接收人收到的消息总数
     *
     * @param recipientId 接收人ID
     * @return 消息总数
     */
    Mono<Long> countByRecipientId(Long recipientId);

    /**
     * 统计接收人的未读消息数量
     *
     * @param recipientId 接收人ID
     * @return 未读消息数量
     */
    Mono<Long> countUnreadByRecipientId(Long recipientId);

    /**
     * 分页查询消息列表
     *
     * @param pageNum 页码，从1开始
     * @param pageSize 每页大小
     * @param status 消息状态，可为null表示查询所有状态
     * @param messageType 消息类型，可为null表示查询所有类型
     * @return 分页结果
     */
    Mono<PageResponse<OffMessageEntity>> findPage(Integer pageNum, Integer pageSize, String status, String messageType);
}
