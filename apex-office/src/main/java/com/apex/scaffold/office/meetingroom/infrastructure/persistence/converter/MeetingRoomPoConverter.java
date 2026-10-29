package com.apex.scaffold.office.meetingroom.infrastructure.persistence.converter;

import com.apex.scaffold.office.meetingroom.domain.entity.MeetingRoomEntity;
import com.apex.scaffold.office.meetingroom.infrastructure.persistence.po.MeetingRoomPO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 会议室 PO 与领域实体转换器
 *
 * @author Apex Scaffold Team
 * @since 1.4.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MeetingRoomPoConverter {

    /**
     * 将持久化对象转换为领域实体。
     *
     * @param po 持久化对象
     * @return 领域实体
     */
    MeetingRoomEntity toDomain(MeetingRoomPO po);

    /**
     * 将领域实体转换为持久化对象。
     *
     * @param entity 领域实体
     * @return 持久化对象
     */
    MeetingRoomPO toPo(MeetingRoomEntity entity);
}
