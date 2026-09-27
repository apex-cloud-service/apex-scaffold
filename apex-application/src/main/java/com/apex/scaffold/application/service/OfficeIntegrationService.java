package com.apex.scaffold.application.service;

import com.apex.scaffold.application.assembler.ApprovalProcessAssembler;
import com.apex.scaffold.application.assembler.CalendarEventAssembler;
import com.apex.scaffold.application.dto.ApprovalProcessDTO;
import com.apex.scaffold.application.dto.CalendarEventDTO;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.repository.OfficeApprovalProcessRepository;
import com.apex.scaffold.domain.repository.OfficeCalendarEventRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 办公集成应用服务
 *
 * <p>提供与飞书、钉钉、企业微信等办公平台集成的核心业务功能，包括审批流程管理和日历事件管理。
 * 支持审批流程的全生命周期管理和日历事件的创建、查询、更新、删除等操作。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>审批管理：创建、更新、删除审批流程，查询审批详情和列表</li>
 *   <li>我的审批：查询用户发起的审批流程</li>
 *   <li>待办审批：查询待当前用户审批的流程列表</li>
 *   <li>审批分页：支持按发起人、状态、时间范围分页查询</li>
 *   <li>日历事件：创建、更新、删除日历事件，查询事件详情和列表</li>
 *   <li>事件分页：支持按时间范围、事件类型分页查询</li>
 * </ul>
 *
 * <h2>集成平台</h2>
 * <ul>
 *   <li>飞书（Feishu/Lark）：审批应用、日历应用</li>
 *   <li>钉钉（DingTalk）：OA审批、日历</li>
 *   <li>企业微信（WeCom）：审批应用、日程</li>
 * </ul>
 *
 * <h2>技术实现</h2>
 * <ul>
 *   <li>使用 Reactor 响应式编程模型，返回 Mono/Flux</li>
 *   <li>通过 Assembler 实现 DTO 与 Entity 之间的双向转换</li>
 *   <li>审批和日历数据分别存储，支持独立的业务逻辑</li>
 *   <li>支持分页查询，返回 PageResponse 统一分页对象</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 * @see OfficeApprovalProcessRepository
 * @see OfficeCalendarEventRepository
 * @see ApprovalProcessAssembler
 * @see CalendarEventAssembler
 */
@Service
@RequiredArgsConstructor
public class OfficeIntegrationService {

    private final OfficeApprovalProcessRepository approvalRepository;
    private final OfficeCalendarEventRepository calendarRepository;
    private final ApprovalProcessAssembler approvalAssembler;
    private final CalendarEventAssembler calendarAssembler;

    /**
     * 创建审批流程
     *
     * @param dto 审批流程 DTO（含审批类型、发起人、表单数据等）
     * @return 创建后的审批流程 DTO
     */
    public Mono<ApprovalProcessDTO> createApproval(ApprovalProcessDTO dto) {
        return Mono.fromCallable(() -> approvalAssembler.toEntity(dto))
                .flatMap(entity -> approvalRepository.insert(entity).thenReturn(approvalAssembler.toDto(entity)));
    }

    /**
     * 更新审批流程
     *
     * <p>更新审批状态、表单数据、审批记录、当前审批人与完成时间。
     *
     * @param id 审批流程 ID
     * @param dto 审批流程 DTO（含待更新的字段值）
     * @return 更新后的审批流程 DTO；审批流程不存在时 Mono 为空
     */
    public Mono<ApprovalProcessDTO> updateApproval(Long id, ApprovalProcessDTO dto) {
        return approvalRepository.findById(id).flatMap(entity -> {
            entity.setStatus(dto.getStatus());
            entity.setFormData(dto.getFormData());
            entity.setRecords(dto.getRecords());
            entity.setCurrentApproverIds(dto.getCurrentApproverIds());
            entity.setCompleteTime(dto.getCompleteTime());
            return approvalRepository.update(entity).thenReturn(approvalAssembler.toDto(entity));
        });
    }

    /**
     * 删除审批流程
     *
     * @param id 审批流程 ID
     * @return 完成信号
     */
    public Mono<Void> deleteApproval(Long id) {
        return approvalRepository.deleteById(id);
    }

    /**
     * 按 ID 查询审批流程详情
     *
     * @param id 审批流程 ID
     * @return 审批流程 DTO；不存在时 Mono 为空
     */
    public Mono<ApprovalProcessDTO> getApprovalById(Long id) {
        return approvalRepository.findById(id).map(approvalAssembler::toDto);
    }

    /**
     * 查询指定用户发起的审批流程列表
     *
     * @param initiatorId 发起人 ID
     * @return 该用户发起的审批流程 DTO 流
     */
    public Flux<ApprovalProcessDTO> getMyApprovals(String initiatorId) {
        return approvalRepository.findByInitiatorId(initiatorId).map(approvalAssembler::toDto);
    }

    /**
     * 查询待指定审批人处理的审批流程列表
     *
     * @param approverId 审批人 ID
     * @return 待该审批人处理的审批流程 DTO 流
     */
    public Flux<ApprovalProcessDTO> getPendingApprovals(String approverId) {
        return approvalRepository.findPendingApprovals(approverId).map(approvalAssembler::toDto);
    }

    /**
     * 分页查询审批流程
     *
     * <p>支持按发起人、审批状态、时间范围过滤。
     *
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页条数
     * @param initiatorId 发起人 ID，可为 null 表示不过滤
     * @param status 审批状态，可为 null 表示不过滤
     * @param startTime 查询起始时间，可为 null 表示不限制
     * @param endTime 查询截止时间，可为 null 表示不限制
     * @return 分页结果（含总条数与当前页审批流程列表）
     */
    public Mono<PageResponse<ApprovalProcessDTO>> getApprovalPage(
            Integer pageNum,
            Integer pageSize,
            String initiatorId,
            String status,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        return approvalRepository
                .findPage(pageNum, pageSize, initiatorId, status, startTime, endTime)
                .map(page -> {
                    PageResponse<ApprovalProcessDTO> result = new PageResponse<>();
                    result.setTotal(page.getTotal());
                    result.setRecords(page.getRecords().stream()
                            .map(approvalAssembler::toDto)
                            .toList());
                    return result;
                });
    }

    /**
     * 创建日历事件
     *
     * @param dto 日历事件 DTO（含标题、起止时间、地点、参会人等）
     * @return 创建后的日历事件 DTO
     */
    public Mono<CalendarEventDTO> createCalendarEvent(CalendarEventDTO dto) {
        return Mono.fromCallable(() -> calendarAssembler.toEntity(dto))
                .flatMap(entity -> calendarRepository.insert(entity).thenReturn(calendarAssembler.toDto(entity)));
    }

    /**
     * 更新日历事件
     *
     * <p>更新标题、描述、事件类型、起止时间、是否全天、地点、参会人、会议室、
     * 提醒时间、重复规则与状态。
     *
     * @param id 日历事件 ID
     * @param dto 日历事件 DTO（含待更新的字段值）
     * @return 更新后的日历事件 DTO；日历事件不存在时 Mono 为空
     */
    public Mono<CalendarEventDTO> updateCalendarEvent(Long id, CalendarEventDTO dto) {
        return calendarRepository.findById(id).flatMap(entity -> {
            entity.setTitle(dto.getTitle());
            entity.setDescription(dto.getDescription());
            entity.setEventType(dto.getEventType());
            entity.setStartTime(dto.getStartTime());
            entity.setEndTime(dto.getEndTime());
            entity.setIsAllDay(dto.getIsAllDay());
            entity.setLocation(dto.getLocation());
            entity.setParticipantIds(dto.getParticipantIds());
            entity.setParticipantNames(dto.getParticipantNames());
            entity.setMeetingRoomId(dto.getMeetingRoomId());
            entity.setReminderMinutes(dto.getReminderMinutes());
            entity.setIsRecurring(dto.getIsRecurring());
            entity.setRecurrenceRule(dto.getRecurrenceRule());
            entity.setStatus(dto.getStatus());
            return calendarRepository.update(entity).thenReturn(calendarAssembler.toDto(entity));
        });
    }

    /**
     * 删除日历事件
     *
     * @param id 日历事件 ID
     * @return 完成信号
     */
    public Mono<Void> deleteCalendarEvent(Long id) {
        return calendarRepository.deleteById(id);
    }

    /**
     * 查询会议室占用情况
     *
     * <p>查询指定会议室在给定时间段内被占用的日历事件，用于判断会议室在该时段是否可用。
     *
     * @param meetingRoomId 会议室 ID
     * @param startTime 查询起始时间
     * @param endTime 查询截止时间
     * @return 占用该会议室的日历事件 DTO 流
     */
    public Flux<CalendarEventDTO> getMeetingRoomAvailability(
            String meetingRoomId, LocalDateTime startTime, LocalDateTime endTime) {
        return calendarRepository
                .findByMeetingRoom(meetingRoomId, startTime, endTime)
                .map(calendarAssembler::toDto);
    }

    /**
     * 按 ID 查询日历事件详情
     *
     * @param id 日历事件 ID
     * @return 日历事件 DTO；不存在时 Mono 为空
     */
    public Mono<CalendarEventDTO> getCalendarEventById(Long id) {
        return calendarRepository.findById(id).map(calendarAssembler::toDto);
    }

    /**
     * 查询用户在指定时间段内参与的日历事件
     *
     * @param userId 用户 ID
     * @param startTime 查询起始时间
     * @param endTime 查询截止时间
     * @return 该用户作为参会人参与且与时间段重叠的日历事件 DTO 流
     */
    public Flux<CalendarEventDTO> getMyCalendarEvents(String userId, LocalDateTime startTime, LocalDateTime endTime) {
        return calendarRepository.findByParticipant(userId, startTime, endTime).map(calendarAssembler::toDto);
    }

    /**
     * 查询用户即将开始的日历事件
     *
     * <p>以当前时间为起点查询该用户参与的即将开始的日历事件。
     *
     * @param userId 用户 ID
     * @param limit 返回条数上限
     * @return 即将开始的日历事件 DTO 流
     */
    public Flux<CalendarEventDTO> getUpcomingEvents(String userId, Integer limit) {
        return calendarRepository
                .findUpcomingEvents(userId, LocalDateTime.now(), limit)
                .map(calendarAssembler::toDto);
    }

    /**
     * 分页查询日历事件
     *
     * <p>支持按组织者、事件类型、状态与时间范围过滤。
     *
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页条数
     * @param organizerId 组织者 ID，可为 null 表示不过滤
     * @param eventType 事件类型，可为 null 表示不过滤
     * @param status 事件状态，可为 null 表示不过滤
     * @param startTime 查询起始时间，可为 null 表示不限制
     * @param endTime 查询截止时间，可为 null 表示不限制
     * @return 分页结果（含总条数与当前页日历事件列表）
     */
    public Mono<PageResponse<CalendarEventDTO>> getCalendarEventPage(
            Integer pageNum,
            Integer pageSize,
            String organizerId,
            String eventType,
            String status,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        return calendarRepository
                .findPage(pageNum, pageSize, organizerId, eventType, status, startTime, endTime)
                .map(page -> {
                    PageResponse<CalendarEventDTO> result = new PageResponse<>();
                    result.setTotal(page.getTotal());
                    result.setRecords(page.getRecords().stream()
                            .map(calendarAssembler::toDto)
                            .toList());
                    return result;
                });
    }
}
