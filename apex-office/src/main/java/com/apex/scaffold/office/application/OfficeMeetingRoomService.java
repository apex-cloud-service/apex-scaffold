package com.apex.scaffold.office.application;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.domain.calendar.CalendarEvent;
import com.apex.scaffold.office.domain.calendar.CalendarEventRepository;
import com.apex.scaffold.office.meetingroom.domain.entity.MeetingRoomEntity;
import com.apex.scaffold.office.meetingroom.domain.repository.MeetingRoomRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 会议室资源应用服务
 *
 * <p>负责会议室资源本身的增删改查、可用性查询与预订/取消编排：
 * <ul>
 *   <li>会议室主数据只属于 office 域，租户作用域由调用方以已认证租户显式传入</li>
 *   <li>占用关系复用日历事件（{@code office_calendar_event.meeting_room_id}），
 *       冲突判断按租户与时间区间重叠计算，并排除已取消事件</li>
 *   <li>预订即创建一条已确认的会议室日历事件，取消即置该事件状态为 CANCELLED</li>
 * </ul>
 *
 * @author Apex Scaffold Team
 * @since 1.4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficeMeetingRoomService {

    /** 预订产生的会议室日历事件所使用的平台标识。 */
    public static final String MEETING_ROOM_PLATFORM = "INTERNAL";

    private final MeetingRoomRepository meetingRoomRepository;
    private final CalendarEventRepository calendarEventRepository;

    /**
     * 创建会议室。
     *
     * @param tenantId 租户标识
     * @param entity 待创建的会议室
     * @return 创建后的会议室（含主键）
     */
    public Mono<MeetingRoomEntity> createMeetingRoom(String tenantId, MeetingRoomEntity entity) {
        entity.setTenantId(tenantId);
        if (entity.getStatus() == null) {
            entity.enable();
        }
        if (entity.getCapacity() != null && entity.getCapacity() <= 0) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "容纳人数必须为正数"));
        }
        // 后续步骤统一 defer，避免前置校验失败时仍触发下游写入调用
        return ensureCodeAvailable(tenantId, entity.getRoomCode(), null)
                .then(Mono.defer(() -> ensureNameAvailable(tenantId, entity.getRoomName(), null)))
                .then(Mono.defer(() -> meetingRoomRepository.insert(entity)))
                .doOnSuccess(saved -> log.info("会议室创建成功 - tenantId: {}, roomId: {}", tenantId, saved.getId()));
    }

    /**
     * 更新会议室。
     *
     * @param tenantId 租户标识
     * @param id 会议室主键
     * @param changes 变更后的会议室数据，主键以路径参数为准
     * @return 更新后的会议室
     */
    public Mono<MeetingRoomEntity> updateMeetingRoom(String tenantId, Long id, MeetingRoomEntity changes) {
        return requireMeetingRoom(tenantId, id)
                .flatMap(existing -> {
                    if (changes.getRoomCode() != null && !changes.getRoomCode().equals(existing.getRoomCode())) {
                        return ensureCodeAvailable(tenantId, changes.getRoomCode(), id)
                                .then(Mono.just(existing));
                    }
                    return Mono.just(existing);
                })
                .flatMap(existing -> {
                    if (changes.getRoomName() != null && !changes.getRoomName().equals(existing.getRoomName())) {
                        return ensureNameAvailable(tenantId, changes.getRoomName(), id)
                                .then(Mono.just(existing));
                    }
                    return Mono.just(existing);
                })
                .flatMap(existing -> {
                    applyChanges(existing, changes);
                    return validateCapacity(existing).then(meetingRoomRepository.update(existing));
                })
                .doOnSuccess(updated -> log.info("会议室更新成功 - tenantId: {}, roomId: {}", tenantId, id));
    }

    /**
     * 删除会议室（逻辑删除）。
     *
     * @param tenantId 租户标识
     * @param id 会议室主键
     * @return 是否删除成功
     */
    public Mono<Boolean> deleteMeetingRoom(String tenantId, Long id) {
        return requireMeetingRoom(tenantId, id)
                .flatMap(room -> meetingRoomRepository.deleteById(tenantId, id))
                .doOnSuccess(
                        deleted -> log.info("会议室删除 - tenantId: {}, roomId: {}, deleted: {}", tenantId, id, deleted));
    }

    /**
     * 查询会议室详情。
     *
     * @param tenantId 租户标识
     * @param id 会议室主键
     * @return 会议室详情
     */
    public Mono<MeetingRoomEntity> getMeetingRoom(String tenantId, Long id) {
        return requireMeetingRoom(tenantId, id);
    }

    /**
     * 分页查询会议室。
     *
     * @param tenantId 租户标识
     * @param pageNum 页码，从 1 开始
     * @param pageSize 每页大小
     * @param status 状态过滤，可为 null
     * @param keyword 名称/编码模糊匹配，可为 null
     * @return 分页结果
     */
    public Mono<PageResponse<MeetingRoomEntity>> listMeetingRooms(
            String tenantId, Integer pageNum, Integer pageSize, String status, String keyword) {
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
        return meetingRoomRepository.findPage(tenantId, safePageNum, safePageSize, status, keyword);
    }

    /**
     * 查询租户内全部启用会议室。
     *
     * @param tenantId 租户标识
     * @return 启用会议室列表
     */
    public Mono<List<MeetingRoomEntity>> listEnabledMeetingRooms(String tenantId) {
        return meetingRoomRepository.findAllEnabled(tenantId).collectList();
    }

    /**
     * 查询指定时间区间内可用的会议室。
     *
     * @param tenantId 租户标识
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 可用会议室列表
     */
    public Mono<List<MeetingRoomEntity>> findAvailableMeetingRooms(
            String tenantId, LocalDateTime startTime, LocalDateTime endTime) {
        return validateTimeRange(startTime, endTime)
                .thenMany(meetingRoomRepository.findAllEnabled(tenantId))
                .concatMap(room -> isAvailable(tenantId, room.getRoomCode(), startTime, endTime)
                        .filter(Boolean::booleanValue)
                        .map(available -> room))
                .collectList();
    }

    /**
     * 判断会议室在指定时间区间是否可用。
     *
     * @param tenantId 租户标识
     * @param roomCode 会议室编码，与日历事件的 {@code meetingRoomId} 一致
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 可用返回 true
     */
    public Mono<Boolean> isAvailable(String tenantId, String roomCode, LocalDateTime startTime, LocalDateTime endTime) {
        return validateTimeRange(startTime, endTime)
                .then(calendarEventRepository.findByMeetingRoom(parseTenantId(tenantId), roomCode, startTime, endTime))
                .map(List::isEmpty);
    }

    /**
     * 预订会议室：创建一条已确认的会议室日历事件。
     *
     * <p>会议室必须存在且处于启用状态；时间区间必须有效且与已有未取消事件不重叠。
     *
     * @param tenantId 租户标识
     * @param roomId 会议室主键
     * @param organizerId 预订人标识
     * @param organizerName 预订人姓名
     * @param title 会议主题，为空时使用会议室名称
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 预订生成的日历事件
     */
    public Mono<CalendarEvent> bookMeetingRoom(
            String tenantId,
            Long roomId,
            String organizerId,
            String organizerName,
            String title,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        return validateTimeRange(startTime, endTime)
                .then(requireMeetingRoom(tenantId, roomId))
                .flatMap(room -> {
                    if (!room.isEnabled()) {
                        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "会议室已停用，无法预订"));
                    }
                    return isAvailable(tenantId, room.getRoomCode(), startTime, endTime)
                            .flatMap(available -> {
                                if (!Boolean.TRUE.equals(available)) {
                                    return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "该时间段会议室已被占用"));
                                }
                                return calendarEventRepository.save(buildBooking(
                                        tenantId, room, organizerId, organizerName, title, startTime, endTime));
                            });
                })
                .doOnSuccess(event ->
                        log.info("会议室预订成功 - tenantId: {}, roomId: {}, eventId: {}", tenantId, roomId, event.getId()));
    }

    /**
     * 取消会议室预订。
     *
     * <p>仅预订人本人可取消，非本人调用按越权处理；重复取消幂等返回当前事件。
     *
     * @param tenantId 租户标识
     * @param roomId 会议室主键
     * @param eventId 预订生成的日历事件主键
     * @param operatorId 当前操作人标识，必须与预订人一致
     * @return 取消后的事件
     */
    public Mono<CalendarEvent> cancelBooking(String tenantId, Long roomId, Long eventId, String operatorId) {
        return requireMeetingRoom(tenantId, roomId)
                .then(calendarEventRepository.findById(eventId))
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "预订记录不存在")))
                .flatMap(event -> {
                    if (!parseTenantId(tenantId).equals(event.getTenantId())) {
                        return Mono.error(new BusinessException(ResultCode.NOT_FOUND, "预订记录不存在"));
                    }
                    if (operatorId == null || !operatorId.equals(event.getOrganizerId())) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "仅预订人本人可取消预订"));
                    }
                    return requireMeetingRoom(tenantId, roomId).map(room -> {
                        if (!room.getRoomCode().equals(event.getMeetingRoomId())) {
                            throw new BusinessException(ResultCode.NOT_FOUND, "预订记录不属于该会议室");
                        }
                        if (event.isCancelled()) {
                            return event;
                        }
                        event.cancel();
                        return event;
                    });
                })
                .flatMap(calendarEventRepository::update)
                .doOnSuccess(event ->
                        log.info("会议室预订取消 - tenantId: {}, roomId: {}, eventId: {}", tenantId, roomId, eventId));
    }

    private CalendarEvent buildBooking(
            String tenantId,
            MeetingRoomEntity room,
            String organizerId,
            String organizerName,
            String title,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        return CalendarEvent.builder()
                .tenantId(parseTenantId(tenantId))
                .platform(MEETING_ROOM_PLATFORM)
                .title(title == null || title.isBlank() ? room.getRoomName() : title)
                .organizerId(organizerId)
                .organizerName(organizerName)
                .startTime(startTime)
                .endTime(endTime)
                .location(room.getLocation())
                .meetingRoomId(room.getRoomCode())
                .isAllDay(false)
                .isRecurring(false)
                .status("CONFIRMED")
                .build();
    }

    private Mono<MeetingRoomEntity> requireMeetingRoom(String tenantId, Long id) {
        if (id == null) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "会议室ID不能为空"));
        }
        return meetingRoomRepository
                .findById(tenantId, id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "会议室不存在")));
    }

    private Mono<Void> ensureCodeAvailable(String tenantId, String roomCode, Long excludeId) {
        if (roomCode == null || roomCode.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "会议室编码不能为空"));
        }
        return meetingRoomRepository
                .findByRoomCode(tenantId, roomCode)
                .filter(existing -> !existing.getId().equals(excludeId))
                .flatMap(existing ->
                        Mono.<MeetingRoomEntity>error(new BusinessException(ResultCode.BUSINESS_ERROR, "会议室编码已存在")))
                .then();
    }

    private Mono<Void> ensureNameAvailable(String tenantId, String roomName, Long excludeId) {
        if (roomName == null || roomName.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "会议室名称不能为空"));
        }
        return meetingRoomRepository
                .findByRoomName(tenantId, roomName)
                .filter(existing -> !existing.getId().equals(excludeId))
                .flatMap(existing ->
                        Mono.<MeetingRoomEntity>error(new BusinessException(ResultCode.BUSINESS_ERROR, "会议室名称已存在")))
                .then();
    }

    private Mono<Void> validateCapacity(MeetingRoomEntity entity) {
        if (entity.getCapacity() != null && entity.getCapacity() <= 0) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "容纳人数必须为正数"));
        }
        return Mono.empty();
    }

    private Mono<Void> validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "开始时间与结束时间不能为空"));
        }
        if (!endTime.isAfter(startTime)) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "结束时间必须晚于开始时间"));
        }
        return Mono.empty();
    }

    /** 日历事件租户为 Long，会议室租户为字符串，跨聚合比较前必须显式转换。 */
    private Long parseTenantId(String tenantId) {
        try {
            return Long.parseLong(tenantId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "租户ID格式错误：" + tenantId);
        }
    }

    private void applyChanges(MeetingRoomEntity target, MeetingRoomEntity changes) {
        if (changes.getRoomCode() != null) {
            target.setRoomCode(changes.getRoomCode());
        }
        if (changes.getRoomName() != null) {
            target.setRoomName(changes.getRoomName());
        }
        if (changes.getCapacity() != null) {
            target.setCapacity(changes.getCapacity());
        }
        if (changes.getLocation() != null) {
            target.setLocation(changes.getLocation());
        }
        if (changes.getFloor() != null) {
            target.setFloor(changes.getFloor());
        }
        if (changes.getEquipment() != null) {
            target.setEquipment(changes.getEquipment());
        }
        if (changes.getStatus() != null) {
            target.setStatus(changes.getStatus());
        }
        if (changes.getRemark() != null) {
            target.setRemark(changes.getRemark());
        }
    }
}
