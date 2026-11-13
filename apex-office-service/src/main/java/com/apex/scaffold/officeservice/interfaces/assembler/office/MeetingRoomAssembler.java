package com.apex.scaffold.officeservice.interfaces.assembler.office;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.domain.calendar.CalendarEvent;
import com.apex.scaffold.office.meetingroom.domain.entity.MeetingRoomEntity;
import com.apex.scaffold.officeservice.interfaces.dto.office.meetingroom.CreateMeetingRoomRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.meetingroom.MeetingRoomBookingResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.meetingroom.MeetingRoomResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.meetingroom.UpdateMeetingRoomRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 会议室 DTO 转换器
 *
 * <p>会议室实体以 JSON 字符串持久化设备清单，接口层对外暴露字符串数组，
 * 因此转换需显式序列化/反序列化，避免把存储细节泄漏给客户端。
 *
 * @author Apex Scaffold Team
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingRoomAssembler {

    private static final TypeReference<List<String>> EQUIPMENT_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    /**
     * 将创建请求转换为会议室实体。
     *
     * @param request 创建请求
     * @return 会议室实体
     */
    public MeetingRoomEntity toEntity(CreateMeetingRoomRequest request) {
        MeetingRoomEntity entity = new MeetingRoomEntity();
        entity.setRoomCode(request.getRoomCode());
        entity.setRoomName(request.getRoomName());
        entity.setCapacity(request.getCapacity());
        entity.setLocation(request.getLocation());
        entity.setFloor(request.getFloor());
        entity.setEquipment(writeEquipment(request.getEquipment()));
        entity.setRemark(request.getRemark());
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            entity.setStatus(request.getStatus());
        }
        return entity;
    }

    /**
     * 将更新请求合并为局部变更实体；仅非空字段参与更新。
     *
     * @param request 更新请求
     * @return 仅包含变更字段的实体
     */
    public MeetingRoomEntity toChanges(UpdateMeetingRoomRequest request) {
        MeetingRoomEntity changes = new MeetingRoomEntity();
        changes.setRoomCode(request.getRoomCode());
        changes.setRoomName(request.getRoomName());
        changes.setCapacity(request.getCapacity());
        changes.setLocation(request.getLocation());
        changes.setFloor(request.getFloor());
        changes.setEquipment(writeEquipment(request.getEquipment()));
        changes.setStatus(request.getStatus());
        changes.setRemark(request.getRemark());
        return changes;
    }

    /**
     * 将会议室实体转换为响应。
     *
     * @param entity 会议室实体
     * @return 会议室响应
     */
    public MeetingRoomResponse toResponse(MeetingRoomEntity entity) {
        return MeetingRoomResponse.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .roomCode(entity.getRoomCode())
                .roomName(entity.getRoomName())
                .capacity(entity.getCapacity())
                .location(entity.getLocation())
                .floor(entity.getFloor())
                .equipment(readEquipment(entity.getEquipment()))
                .status(entity.getStatus())
                .remark(entity.getRemark())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    /**
     * 将会议室实体列表转换为响应列表。
     *
     * @param entities 会议室实体列表
     * @return 会议室响应列表
     */
    public List<MeetingRoomResponse> toResponseList(List<MeetingRoomEntity> entities) {
        return entities.stream().map(this::toResponse).toList();
    }

    /**
     * 将预订事件与会议室信息组合为预订响应。
     *
     * @param event 预订生成的日历事件
     * @param room 会议室实体
     * @return 预订结果响应
     */
    public MeetingRoomBookingResponse toBookingResponse(CalendarEvent event, MeetingRoomEntity room) {
        return MeetingRoomBookingResponse.builder()
                .eventId(event.getId())
                .meetingRoomId(room.getId())
                .meetingRoomCode(room.getRoomCode())
                .title(event.getTitle())
                .organizerId(event.getOrganizerId())
                .organizerName(event.getOrganizerName())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .status(event.getStatus())
                .location(event.getLocation())
                .build();
    }

    private String writeEquipment(List<String> equipment) {
        if (equipment == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(equipment);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "设备清单格式错误");
        }
    }

    private List<String> readEquipment(String equipment) {
        if (equipment == null || equipment.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(equipment, EQUIPMENT_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("会议室设备清单反序列化失败，按空列表返回 - equipment: {}", equipment);
            return List.of();
        }
    }
}
