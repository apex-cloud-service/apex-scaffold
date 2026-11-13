package com.apex.scaffold.office.meetingroom.domain.entity;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 会议室资源实体
 *
 * <p>表示租户内可供预订的会议室。会议室与日历事件通过 {@code office_calendar_event.meeting_room_id}
 * 弱关联（字符串主键），可用性判断由应用服务按时间区间与状态计算，不在实体内缓存占用结果。
 *
 * @author Apex Scaffold Team
 * @since 1.3.2
 */
@Getter
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class MeetingRoomEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 启用状态：可被预订。 */
    public static final String STATUS_ENABLED = "ENABLED";

    /** 停用状态：不可被预订，但仍保留历史占用记录。 */
    public static final String STATUS_DISABLED = "DISABLED";

    private String tenantId;
    private String roomCode;
    private String roomName;
    private Integer capacity;
    private String location;
    private String floor;
    private String equipment;
    private String status;
    private String remark;

    /**
     * 设置租户 ID，并刷新实体更新时间。
     *
     * @param tenantId 会议室所属租户标识
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
        touch();
    }

    /**
     * 设置会议室编码，并刷新实体更新时间。
     *
     * @param roomCode 租户内唯一的会议室编码
     */
    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
        touch();
    }

    /**
     * 设置会议室名称，并刷新实体更新时间。
     *
     * @param roomName 会议室的展示名称
     */
    public void setRoomName(String roomName) {
        this.roomName = roomName;
        touch();
    }

    /**
     * 设置容纳人数，并刷新实体更新时间。
     *
     * @param capacity 会议室可容纳的最大人数
     */
    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
        touch();
    }

    /**
     * 设置位置描述，并刷新实体更新时间。
     *
     * @param location 会议室所在位置描述
     */
    public void setLocation(String location) {
        this.location = location;
        touch();
    }

    /**
     * 设置楼层，并刷新实体更新时间。
     *
     * @param floor 会议室所在楼层
     */
    public void setFloor(String floor) {
        this.floor = floor;
        touch();
    }

    /**
     * 设置设备清单，并刷新实体更新时间。
     *
     * @param equipment 设备清单的 JSON 字符串
     */
    public void setEquipment(String equipment) {
        this.equipment = equipment;
        touch();
    }

    /**
     * 设置会议室状态，并刷新实体更新时间。
     *
     * @param status {@link #STATUS_ENABLED} 或 {@link #STATUS_DISABLED}
     */
    public void setStatus(String status) {
        this.status = status;
        touch();
    }

    /**
     * 设置备注，并刷新实体更新时间。
     *
     * @param remark 会议室补充说明
     */
    public void setRemark(String remark) {
        this.remark = remark;
        touch();
    }

    /**
     * 判断会议室是否处于可预订状态。
     *
     * @return 状态为 {@link #STATUS_ENABLED} 时返回 true
     */
    public boolean isEnabled() {
        return STATUS_ENABLED.equals(status);
    }

    /**
     * 启用会议室。
     */
    public void enable() {
        setStatus(STATUS_ENABLED);
    }

    /**
     * 停用会议室。
     */
    public void disable() {
        setStatus(STATUS_DISABLED);
    }
}
