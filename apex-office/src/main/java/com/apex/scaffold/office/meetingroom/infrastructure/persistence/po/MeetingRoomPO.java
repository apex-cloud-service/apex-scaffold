package com.apex.scaffold.office.meetingroom.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 会议室资源持久化对象
 *
 * <p>对应数据库表 {@code office_meeting_room}。承接租户隔离字段 {@code tenant_id} 与
 * {@link BasePO} 提供的通用字段（雪花主键、时间戳、逻辑删除、乐观锁版本）。
 *
 * @author Apex Scaffold Team
 * @since 1.4.0
 * @see BasePO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("office_meeting_room")
public class MeetingRoomPO extends BasePO {

    @TableField("tenant_id")
    private String tenantId;

    @TableField("room_code")
    private String roomCode;

    @TableField("room_name")
    private String roomName;

    @TableField("capacity")
    private Integer capacity;

    @TableField("location")
    private String location;

    @TableField("floor")
    private String floor;

    @TableField("equipment")
    private String equipment;

    @TableField("status")
    private String status;

    @TableField("remark")
    private String remark;
}
