package com.apex.scaffold.domain.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.model.entity.OfficeCalendarEventEntity;
import java.time.LocalDateTime;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 办公日历事件仓储接口
 *
 * <p>定义办公平台日历事件的持久化操作契约，支持多平台（飞书、钉钉、企业微信）的日历事件管理。
 * 提供日历事件的增删改查、时间范围查询、参与人查询、会议室查询等功能。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>日历事件的基础 CRUD 操作</li>
 *   <li>按时间范围查询事件（日、周、月视图）</li>
 *   <li>按组织者、参与人、会议室查询事件</li>
 *   <li>查询即将到来的事件（待办提醒）</li>
 *   <li>分页查询和条件过滤</li>
 *   <li>多平台事件ID映射和同步</li>
 * </ul>
 *
 * <h2>查询场景</h2>
 * <ul>
 *   <li>个人日历视图：按用户查询某时间段的所有事件</li>
 *   <li>会议室预定：查询会议室在某时间段的占用情况</li>
 *   <li>待办提醒：查询用户即将开始的事件</li>
 *   <li>参与人视图：查询某人参与的所有事件</li>
 *   <li>事件管理：分页查询、条件筛选、状态管理</li>
 * </ul>
 *
 * <h2>技术特性</h2>
 * <ul>
 *   <li>响应式编程：所有方法返回 Mono/Flux，支持非阻塞操作</li>
 *   <li>多平台支持：通过 platform + platformEventId 实现平台事件映射</li>
 *   <li>时间范围查询：支持跨天、跨周、跨月的事件查询</li>
 *   <li>性能优化：索引优化、分页查询、限制返回数量</li>
 * </ul>
 *
 * <h2>实现要求</h2>
 * <ul>
 *   <li>基础设施层实现：由 MyBatis-Plus 实现具体持久化逻辑</li>
 *   <li>租户隔离：所有查询和操作必须考虑租户上下文</li>
 *   <li>阻塞调度：数据库 I/O 操作必须使用 BlockingIoSchedulers.jdbc()</li>
 *   <li>事务支持：写操作应支持事务边界管理</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 * @see OfficeCalendarEventEntity
 */
public interface OfficeCalendarEventRepository {

    /**
     * 根据日历事件ID查询事件信息
     *
     * @param id 日历事件ID
     * @return 日历事件实体的Mono包装，如果不存在则返回空Mono
     */
    Mono<OfficeCalendarEventEntity> findById(Long id);

    /**
     * 根据平台事件ID查询日历事件
     *
     * @param platform 平台标识（如feishu、dingtalk、wechat）
     * @param platformEventId 平台日历事件ID
     * @return 日历事件实体的Mono包装，如果不存在则返回空Mono
     */
    Mono<OfficeCalendarEventEntity> findByPlatformEventId(String platform, String platformEventId);

    /**
     * 查询组织者创建的所有日历事件
     *
     * @param organizerId 组织者ID
     * @return 日历事件列表的Flux流
     */
    Flux<OfficeCalendarEventEntity> findByOrganizerId(String organizerId);

    /**
     * 查询指定时间范围内的所有日历事件
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 日历事件列表的Flux流
     */
    Flux<OfficeCalendarEventEntity> findByTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 查询参与人在指定时间范围内的日历事件
     *
     * @param participantId 参与人ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 日历事件列表的Flux流
     */
    Flux<OfficeCalendarEventEntity> findByParticipant(
            String participantId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 查询会议室在指定时间范围内的预定情况
     *
     * @param meetingRoomId 会议室ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 日历事件列表的Flux流
     */
    Flux<OfficeCalendarEventEntity> findByMeetingRoom(
            String meetingRoomId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 查询用户即将到来的日历事件
     *
     * @param userId 用户ID
     * @param fromTime 起始时间
     * @param limit 限制返回数量
     * @return 即将到来的日历事件列表的Flux流
     */
    Flux<OfficeCalendarEventEntity> findUpcomingEvents(String userId, LocalDateTime fromTime, Integer limit);

    /**
     * 插入新日历事件记录
     *
     * @param entity 日历事件实体
     * @return 受影响的行数
     */
    Mono<Integer> insert(OfficeCalendarEventEntity entity);

    /**
     * 更新日历事件信息
     *
     * @param entity 日历事件实体，必须包含ID
     * @return 受影响的行数
     */
    Mono<Integer> update(OfficeCalendarEventEntity entity);

    /**
     * 根据ID删除日历事件（逻辑删除）
     *
     * @param id 日历事件ID
     * @return 删除操作的Mono包装
     */
    Mono<Void> deleteById(Long id);

    /**
     * 分页查询日历事件列表
     *
     * @param pageNum 页码，从1开始
     * @param pageSize 每页大小
     * @param organizerId 组织者ID，可为null表示查询所有组织者
     * @param eventType 事件类型，可为null表示查询所有类型
     * @param status 事件状态，可为null表示查询所有状态
     * @param startTime 开始时间，可为null表示不限制
     * @param endTime 结束时间，可为null表示不限制
     * @return 分页结果
     */
    Mono<PageResponse<OfficeCalendarEventEntity>> findPage(
            Integer pageNum,
            Integer pageSize,
            String organizerId,
            String eventType,
            String status,
            LocalDateTime startTime,
            LocalDateTime endTime);
}
