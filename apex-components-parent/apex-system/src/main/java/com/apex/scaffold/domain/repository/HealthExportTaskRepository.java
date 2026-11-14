package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.monitor.HealthExportTaskEntity;
import java.time.LocalDateTime;
import reactor.core.publisher.Mono;

/**
 * 体检快照异步导出任务仓储端口。
 *
 * <p>任务表是「多实例竞争执行」的工作队列，认领、回写与清理都通过条件更新完成，
 * 以保证同一个任务同一时刻只有一个实例在处理。</p>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 * @see com.apex.scaffold.domain.model.monitor.HealthExportTaskEntity
 */
public interface HealthExportTaskRepository {

    /**
     * 新增导出任务。
     *
     * @param entity 任务实体（状态应为 {@code PENDING}）
     * @return 已落库的任务实体（含主键）
     */
    Mono<HealthExportTaskEntity> insert(HealthExportTaskEntity entity);

    /**
     * 按主键查询任务。
     *
     * @param id 任务主键
     * @return 任务实体，不存在时为空
     */
    Mono<HealthExportTaskEntity> findById(Long id);

    /**
     * 查询指定租户下一条可认领任务。
     *
     * @param tenantId 租户标识
     * @param maxAttempts 最大尝试次数
     * @param staleBefore 认领租约过期阈值
     * @return 候选任务，无候选时为空
     */
    Mono<HealthExportTaskEntity> findNextCandidate(String tenantId, int maxAttempts, LocalDateTime staleBefore);

    /**
     * 竞争认领任务。
     *
     * @param id 任务主键
     * @param tenantId 租户标识
     * @param workerId 认领实例标识
     * @param staleBefore 认领租约过期阈值
     * @return 是否认领成功
     */
    Mono<Boolean> claim(Long id, String tenantId, String workerId, LocalDateTime staleBefore);

    /**
     * 回写导出成功结果。
     *
     * @param id 任务主键
     * @param tenantId 租户标识
     * @param fileName 文件名
     * @param rowCount 记录数
     * @param content 文件内容
     * @return 是否更新成功
     */
    Mono<Boolean> markCompleted(Long id, String tenantId, String fileName, int rowCount, byte[] content);

    /**
     * 回写导出失败原因。
     *
     * @param id 任务主键
     * @param tenantId 租户标识
     * @param error 失败原因
     * @return 是否更新成功
     */
    Mono<Boolean> markFailed(Long id, String tenantId, String error);

    /**
     * 物理删除过期的已完成任务，回收文件内容占用的存储。
     *
     * @param tenantId 租户标识
     * @param before 完成时间阈值（不含）
     * @param limit 单次删除条数
     * @return 实际删除条数
     */
    Mono<Integer> purgeCompletedBefore(String tenantId, LocalDateTime before, int limit);
}
