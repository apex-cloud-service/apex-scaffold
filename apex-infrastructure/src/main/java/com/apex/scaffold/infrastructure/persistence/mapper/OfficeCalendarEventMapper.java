package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.OfficeCalendarEventPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 办公日历事件 MyBatis Mapper 接口
 *
 * <p>负责 {@code office_calendar_event} 表的持久化操作，管理从第三方办公平台同步的日历事件信息。
 * 继承 MyBatis-Plus {@link BaseMapper} 获得通用单表 CRUD 操作。
 *
 * <h2>表映射</h2>
 * <p>表名：{@code office_calendar_event}<br>
 * 实体：{@link OfficeCalendarEventPO}<br>
 * 主键：{@code id}（Long 类型，雪花算法）
 *
 * <h2>日历事件字段</h2>
 * <ul>
 *   <li>{@code event_title}：事件标题</li>
 *   <li>{@code event_description}：事件描述</li>
 *   <li>{@code start_time}：开始时间</li>
 *   <li>{@code end_time}：结束时间</li>
 *   <li>{@code location}：地点</li>
 *   <li>{@code organizer_id}：组织者用户 ID</li>
 *   <li>{@code attendee_ids}：参与者 ID 列表（JSON 数组）</li>
 *   <li>{@code event_type}：事件类型（MEETING、REMINDER、HOLIDAY）</li>
 *   <li>{@code status}：事件状态（SCHEDULED、COMPLETED、CANCELLED）</li>
 *   <li>{@code external_event_id}：外部平台事件 ID</li>
 *   <li>{@code platform}：来源平台（FEISHU、DINGTALK、WECOM）</li>
 *   <li>{@code recurrence_rule}：重复规则（iCalendar RRULE 格式）</li>
 * </ul>
 *
 * <h2>事件类型</h2>
 * <ul>
 *   <li>MEETING：会议事件，包含参与者和会议室信息</li>
 *   <li>REMINDER：提醒事件，个人待办提醒</li>
 *   <li>HOLIDAY：假期事件，节假日标记</li>
 *   <li>TASK：任务事件，关联任务的时间安排</li>
 * </ul>
 *
 * <h2>同步来源</h2>
 * <ul>
 *   <li>飞书（Lark）：从飞书日历同步会议和日程</li>
 *   <li>钉钉（DingTalk）：从钉钉日历同步日程安排</li>
 *   <li>企业微信（WeChat Work）：从企业微信日历同步</li>
 * </ul>
 *
 * <h2>业务场景</h2>
 * <ul>
 *   <li>日程查询：按时间范围查询用户的日历事件</li>
 *   <li>会议提醒：即将开始的会议提醒</li>
 *   <li>时间冲突检测：检查新事件与已有事件的时间冲突</li>
 *   <li>参与者管理：查询事件的所有参与者</li>
 * </ul>
 *
 * <h2>通用方法</h2>
 * <p>继承自 {@link BaseMapper} 的常用方法：
 * <ul>
 *   <li>{@code insert(entity)} - 插入日历事件</li>
 *   <li>{@code updateById(entity)} - 更新事件信息</li>
 *   <li>{@code selectById(id)} - 根据 ID 查询事件</li>
 *   <li>{@code selectList(wrapper)} - 条件查询事件列表</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see OfficeCalendarEventPO
 * @see BaseMapper
 */
@Mapper
public interface OfficeCalendarEventMapper extends BaseMapper<OfficeCalendarEventPO> {}
