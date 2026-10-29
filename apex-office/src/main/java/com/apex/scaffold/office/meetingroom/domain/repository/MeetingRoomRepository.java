package com.apex.scaffold.office.meetingroom.domain.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.office.meetingroom.domain.entity.MeetingRoomEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 会议室资源仓储接口
 *
 * <p>定义会议室资源的持久化契约。所有查询均按租户隔离，调用方必须显式传入租户标识。
 *
 * @author Apex Scaffold Team
 * @since 1.4.0
 * @see MeetingRoomEntity
 */
public interface MeetingRoomRepository {

    /**
     * 按主键与租户查询会议室。
     *
     * @param tenantId 租户标识
     * @param id 会议室主键
     * @return 会议室实体，不存在时为空 Mono
     */
    Mono<MeetingRoomEntity> findById(String tenantId, Long id);

    /**
     * 按租户与会议室编码查询，用于编码唯一性校验。
     *
     * @param tenantId 租户标识
     * @param roomCode 会议室编码
     * @return 会议室实体，不存在时为空 Mono
     */
    Mono<MeetingRoomEntity> findByRoomCode(String tenantId, String roomCode);

    /**
     * 按租户与会议室名称查询，用于名称唯一性校验。
     *
     * @param tenantId 租户标识
     * @param roomName 会议室名称
     * @return 会议室实体，不存在时为空 Mono
     */
    Mono<MeetingRoomEntity> findByRoomName(String tenantId, String roomName);

    /**
     * 插入会议室记录并回填主键。
     *
     * @param entity 会议室实体
     * @return 插入完成信号
     */
    Mono<MeetingRoomEntity> insert(MeetingRoomEntity entity);

    /**
     * 更新会议室记录。
     *
     * @param entity 会议室实体，必须包含主键
     * @return 更新完成信号
     */
    Mono<MeetingRoomEntity> update(MeetingRoomEntity entity);

    /**
     * 逻辑删除会议室。
     *
     * @param tenantId 租户标识
     * @param id 会议室主键
     * @return 是否删除成功
     */
    Mono<Boolean> deleteById(String tenantId, Long id);

    /**
     * 分页查询会议室。
     *
     * @param tenantId 租户标识
     * @param pageNum 页码，从 1 开始
     * @param pageSize 每页大小
     * @param status 状态过滤，为 null 表示不限
     * @param keyword 名称或编码模糊匹配，为 null 表示不限
     * @return 分页结果
     */
    Mono<PageResponse<MeetingRoomEntity>> findPage(
            String tenantId, Integer pageNum, Integer pageSize, String status, String keyword);

    /**
     * 查询租户内全部启用状态的会议室，用于预订时的下拉选择。
     *
     * @param tenantId 租户标识
     * @return 启用会议室列表
     */
    Flux<MeetingRoomEntity> findAllEnabled(String tenantId);
}
