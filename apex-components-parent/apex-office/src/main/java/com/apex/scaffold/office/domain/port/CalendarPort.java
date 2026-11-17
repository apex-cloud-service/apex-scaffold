package com.apex.scaffold.office.domain.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * 日历端口接口
 *
 * <p>定义日历领域的外部依赖接口，遵循端口-适配器架构模式。
 * 该接口由 Domain 层定义，由 Infrastructure 层实现，Application 层使用。
 *
 * <p>这是一个出站端口（Outbound Port），用于与外部办公平台的日历系统交互。
 * 通过依赖倒置原则，Application 层依赖此接口而非具体实现，保持架构清晰。
 *
 * @author Apex Scaffold
 * @since 1.2.0
 */
public interface CalendarPort {

    /**
     * 创建日历事件
     *
     * @param organizerId 组织者ID（在办公平台中的用户ID）
     * @param title 事件标题
     * @param description 事件描述
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param participantIds 参与人ID列表
     * @param location 事件地点
     * @param reminderMinutes 提醒时间（分钟数）
     * @return 平台返回的事件ID
     */
    Mono<String> createEvent(
            String organizerId,
            String title,
            String description,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String[] participantIds,
            String location,
            Integer reminderMinutes);

    /**
     * 查询日历事件详情。
     *
     * <p>飞书/钉钉的事件以日历为父资源，仅有事件ID无法定位，故需同时提供组织者
     * （其主日历即父资源）。实现方不得在缺少该标识时猜测归属。
     *
     * @param platformEventId 平台中的事件ID
     * @param organizerId 组织者ID（平台侧用户标识，用作主日历）
     * @return 事件详情
     */
    Mono<Map<String, Object>> getEventDetail(String platformEventId, String organizerId);

    /**
     * 更新日历事件
     *
     * @param platformEventId 平台中的事件ID
     * @param title 事件标题
     * @param description 事件描述
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param participantIds 参与人ID列表
     * @param location 事件地点
     * @return 更新是否成功
     */
    Mono<Boolean> updateEvent(
            String platformEventId,
            String organizerId,
            String title,
            String description,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String[] participantIds,
            String location);

    /**
     * 取消日历事件
     *
     * @param platformEventId 平台中的事件ID
     * @param organizerId 组织者ID
     * @return 取消是否成功
     */
    Mono<Boolean> cancelEvent(String platformEventId, String organizerId);

    /**
     * 删除日历事件
     *
     * @param platformEventId 平台中的事件ID
     * @param organizerId 组织者ID
     * @return 删除是否成功
     */
    Mono<Boolean> deleteEvent(String platformEventId, String organizerId);

    /**
     * 查询指定时间范围内的事件列表
     *
     * @param userId 用户ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 事件列表
     */
    Mono<List<Map<String, Object>>> listEvents(String userId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 查询会议室在指定时间范围内的可用性
     *
     * @param meetingRoomId 会议室ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 是否可用
     */
    Mono<Boolean> checkMeetingRoomAvailability(String meetingRoomId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取支持的平台类型
     *
     * @return 平台类型标识（feishu/dingtalk/wecom）
     */
    String getPlatformType();
}
