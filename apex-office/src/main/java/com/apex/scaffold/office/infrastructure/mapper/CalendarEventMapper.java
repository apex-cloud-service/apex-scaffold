package com.apex.scaffold.office.infrastructure.mapper;

import com.apex.scaffold.office.infrastructure.po.CalendarEventPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 日历事件Mapper接口
 *
 * <p>提供日历事件的数据库访问操作，继承MyBatis-Plus的BaseMapper获得基础CRUD能力。
 * 自定义查询方法使用@Select注解定义SQL语句。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Mapper
public interface CalendarEventMapper extends BaseMapper<CalendarEventPO> {

    /**
     * 根据平台和平台事件ID查询日历事件
     *
     * @param platform 办公平台类型
     * @param platformEventId 平台中的事件ID
     * @return 日历事件PO
     */
    @Select(
            "SELECT * FROM office_calendar_event WHERE platform = #{platform} AND platform_event_id = #{platformEventId} AND deleted = 0")
    CalendarEventPO selectByPlatformAndEventId(
            @Param("platform") String platform, @Param("platformEventId") String platformEventId);

    /**
     * 查询指定时间范围内的日历事件
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 日历事件列表
     */
    @Select("SELECT * FROM office_calendar_event WHERE deleted = 0 AND "
            + "((start_time >= #{startTime} AND start_time < #{endTime}) OR "
            + "(end_time > #{startTime} AND end_time <= #{endTime}) OR "
            + "(start_time <= #{startTime} AND end_time >= #{endTime})) "
            + "ORDER BY start_time ASC")
    List<CalendarEventPO> selectByTimeRange(
            @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 查询指定组织者的日历事件
     *
     * @param organizerId 组织者ID
     * @return 日历事件列表
     */
    @Select(
            "SELECT * FROM office_calendar_event WHERE organizer_id = #{organizerId} AND deleted = 0 ORDER BY start_time DESC")
    List<CalendarEventPO> selectByOrganizer(@Param("organizerId") String organizerId);

    /**
     * 查询指定参与人的日历事件
     *
     * @param participantId 参与人ID
     * @return 日历事件列表
     */
    @Select(
            "SELECT * FROM office_calendar_event WHERE participant_ids LIKE CONCAT('%', #{participantId}, '%') AND deleted = 0 ORDER BY start_time DESC")
    List<CalendarEventPO> selectByParticipant(@Param("participantId") String participantId);

    /**
     * 查询指定会议室在指定时间范围内的日历事件
     *
     * @param meetingRoomId 会议室ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 日历事件列表
     */
    @Select("SELECT * FROM office_calendar_event WHERE meeting_room_id = #{meetingRoomId} AND deleted = 0 AND "
            + "((start_time >= #{startTime} AND start_time < #{endTime}) OR "
            + "(end_time > #{startTime} AND end_time <= #{endTime}) OR "
            + "(start_time <= #{startTime} AND end_time >= #{endTime})) "
            + "ORDER BY start_time ASC")
    List<CalendarEventPO> selectByMeetingRoom(
            @Param("meetingRoomId") String meetingRoomId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
