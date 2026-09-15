package com.apex.scaffold.infrastructure.office.client;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 办公平台日历客户端接口
 *
 * <p>定义与各办公平台日历API交互的统一接口，屏蔽不同平台的API差异。
 * 支持飞书、钉钉、企业微信等多个办公平台的日历功能对接。
 *
 * <p>实现类需要处理：
 * <ul>
 *   <li>访问令牌的获取和刷新</li>
 *   <li>API请求的构建和发送</li>
 *   <li>响应结果的解析和错误处理</li>
 *   <li>平台特有参数的适配</li>
 *   <li>时区转换和时间格式处理</li>
 * </ul>
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
public interface CalendarClient {

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
            Integer reminderMinutes
    );

    /**
     * 查询日历事件详情
     *
     * @param platformEventId 平台中的事件ID
     * @return 事件详情
     */
    Mono<Map<String, Object>> getEventDetail(String platformEventId);

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
            String title,
            String description,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String[] participantIds,
            String location
    );

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
