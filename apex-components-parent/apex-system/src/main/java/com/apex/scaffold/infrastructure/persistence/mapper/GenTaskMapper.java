package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.GenTaskPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 代码生成器异步生成任务 Mapper。
 *
 * <p>生成任务属于租户级数据，候选查询、认领与状态回写全部带 {@code tenant_id} 条件，
 * 因为以下 SQL 是注解手写，不会经过持久化拦截器的自动租户追加。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.5.0
 * @see GenTaskPO
 */
@Mapper
public interface GenTaskMapper extends BaseMapper<GenTaskPO> {

    /**
     * 查找一条可认领的生成任务。
     *
     * <p>可认领包含两类：待处理且未超过最大尝试次数的任务，以及认领后长时间未更新
     * 且尝试次数未达上限的 RUNNING 任务（实例崩溃遗留，允许其他实例接管）。两类候选
     * 都受 {@code attempts < maxAttempts} 约束，保证耗尽重试次数的任务停在终局等待
     * 人工介入，不会被接管分支无限重启。
     *
     * @param tenantId      租户标识
     * @param maxAttempts   最大尝试次数
     * @param leaseDeadline 认领租约过期阈值
     * @return 候选任务；无候选返回 {@code null}
     */
    @Select("SELECT * FROM gen_task "
            + "WHERE deleted=0 AND tenant_id=#{tenantId} "
            + "AND ((status='PENDING' AND attempts < #{maxAttempts}) "
            + "OR (status='RUNNING' AND locked_at < #{leaseDeadline} AND attempts < #{maxAttempts})) "
            + "ORDER BY create_time ASC, id ASC LIMIT 1")
    GenTaskPO findNextCandidate(
            @Param("tenantId") String tenantId,
            @Param("maxAttempts") int maxAttempts,
            @Param("leaseDeadline") LocalDateTime leaseDeadline);

    /**
     * 竞争认领任务。
     *
     * @param id            任务标识
     * @param tenantId      租户标识
     * @param lockedBy      认领实例标识
     * @param lockedAt      认领时间
     * @param leaseDeadline 认领租约过期阈值
     * @return 影响行数（1=认领成功，0=已被其他实例抢先或已取消）
     */
    @Update("UPDATE gen_task SET status='RUNNING', attempts=attempts+1, "
            + "locked_at=#{lockedAt}, locked_by=#{lockedBy}, update_time=NOW() "
            + "WHERE id=#{id} AND deleted=0 AND tenant_id=#{tenantId} "
            + "AND (status='PENDING' OR (status='RUNNING' AND locked_at < #{leaseDeadline}))")
    int claim(
            @Param("id") Long id,
            @Param("tenantId") String tenantId,
            @Param("lockedBy") String lockedBy,
            @Param("lockedAt") LocalDateTime lockedAt,
            @Param("leaseDeadline") LocalDateTime leaseDeadline);

    /**
     * 续租：刷新认领时间，避免长任务被其他实例误判为遗弃后重复接管。
     *
     * @param id       任务标识
     * @param tenantId 租户标识
     * @param lockedBy 认领实例标识
     * @param lockedAt 新的认领时间
     * @return 影响行数
     */
    @Update("UPDATE gen_task SET locked_at=#{lockedAt}, update_time=NOW() "
            + "WHERE id=#{id} AND deleted=0 AND tenant_id=#{tenantId} "
            + "AND status='RUNNING' AND locked_by=#{lockedBy}")
    int renewLease(
            @Param("id") Long id,
            @Param("tenantId") String tenantId,
            @Param("lockedBy") String lockedBy,
            @Param("lockedAt") LocalDateTime lockedAt);

    /**
     * 回写处理进度并顺延续租。
     *
     * <p>WHERE 同时约束 {@code status='RUNNING'} 与 {@code locked_by=#{lockedBy}}：
     * 任务被取消、已进入终态或租约被其他实例接管后，旧执行者的进度写入不再生效，
     * 调用方以 0 行更新作为「停止处理」信号。SET 子句刷新 {@code locked_at}，
     * 使每次进度推进同时充当租约心跳，长任务不会被误判为遗弃。
     *
     * @param id             任务标识
     * @param tenantId       租户标识
     * @param lockedBy       认领实例标识
     * @param lockedAt       本次心跳时间
     * @param totalCount     总处理单元数
     * @param processedCount 已处理单元数
     * @param failedCount    失败单元数
     * @param errorMessage   最后一次单元错误摘要
     * @return 影响行数（0=任务已取消/终态/被接管）
     */
    @Update("UPDATE gen_task SET total_count=#{totalCount}, processed_count=#{processedCount}, "
            + "failed_count=#{failedCount}, error_message=#{errorMessage}, "
            + "locked_at=#{lockedAt}, update_time=NOW() "
            + "WHERE id=#{id} AND deleted=0 AND tenant_id=#{tenantId} "
            + "AND status='RUNNING' AND locked_by=#{lockedBy}")
    int updateProgress(
            @Param("id") Long id,
            @Param("tenantId") String tenantId,
            @Param("lockedBy") String lockedBy,
            @Param("lockedAt") LocalDateTime lockedAt,
            @Param("totalCount") int totalCount,
            @Param("processedCount") int processedCount,
            @Param("failedCount") int failedCount,
            @Param("errorMessage") String errorMessage);

    /**
     * 标记任务完成并回写生成历史。
     *
     * <p>WHERE 限定 {@code locked_by=#{lockedBy}}：任务被其他实例接管后，旧执行者的
     * 终态写入不生效，避免两个实例对同一任务写冲突终态。
     *
     * @param id             任务标识
     * @param tenantId       租户标识
     * @param lockedBy       认领实例标识
     * @param historyId      生成历史 ID
     * @param totalCount     总处理单元数
     * @param processedCount 已处理单元数
     * @param failedCount    失败单元数
     * @param completedAt    完成时间
     * @return 影响行数（0=任务已取消/终态/被接管）
     */
    @Update("UPDATE gen_task SET status='COMPLETED', history_id=#{historyId}, "
            + "total_count=#{totalCount}, processed_count=#{processedCount}, failed_count=#{failedCount}, "
            + "completed_at=#{completedAt}, locked_at=NULL, locked_by=NULL, update_time=NOW() "
            + "WHERE id=#{id} AND deleted=0 AND tenant_id=#{tenantId} "
            + "AND status='RUNNING' AND locked_by=#{lockedBy}")
    int markCompleted(
            @Param("id") Long id,
            @Param("tenantId") String tenantId,
            @Param("lockedBy") String lockedBy,
            @Param("historyId") Long historyId,
            @Param("totalCount") int totalCount,
            @Param("processedCount") int processedCount,
            @Param("failedCount") int failedCount,
            @Param("completedAt") LocalDateTime completedAt);

    /**
     * 标记任务失败，保留已累加的尝试次数供后续重试判断。
     *
     * <p>WHERE 限定 {@code locked_by=#{lockedBy}}，语义与 {@link #markCompleted} 相同：
     * 仅当前认领者可以落失败终态。
     *
     * @param id          任务标识
     * @param tenantId    租户标识
     * @param lockedBy    认领实例标识
     * @param errorMessage 失败原因（已截断）
     * @param completedAt 完成时间
     * @return 影响行数（0=任务已取消/终态/被接管）
     */
    @Update("UPDATE gen_task SET status='FAILED', error_message=#{errorMessage}, "
            + "completed_at=#{completedAt}, locked_at=NULL, locked_by=NULL, update_time=NOW() "
            + "WHERE id=#{id} AND deleted=0 AND tenant_id=#{tenantId} "
            + "AND status='RUNNING' AND locked_by=#{lockedBy}")
    int markFailed(
            @Param("id") Long id,
            @Param("tenantId") String tenantId,
            @Param("lockedBy") String lockedBy,
            @Param("errorMessage") String errorMessage,
            @Param("completedAt") LocalDateTime completedAt);

    /**
     * 请求取消任务，仅对非终态任务生效。
     *
     * @param id         任务标识
     * @param tenantId   租户标识
     * @param canceledAt 取消时间
     * @return 影响行数（0=任务已是终态）
     */
    @Update("UPDATE gen_task SET status='CANCELED', canceled_at=#{canceledAt}, completed_at=#{canceledAt}, "
            + "locked_at=NULL, locked_by=NULL, update_time=NOW() "
            + "WHERE id=#{id} AND deleted=0 AND tenant_id=#{tenantId} "
            + "AND status IN ('PENDING','RUNNING')")
    int requestCancel(
            @Param("id") Long id, @Param("tenantId") String tenantId, @Param("canceledAt") LocalDateTime canceledAt);

    /**
     * 查找一批已过保留期的终态任务主键。
     *
     * <p>清理采用「先查主键、后按主键删除」的两步实现而非
     * {@code DELETE ... ORDER BY ... LIMIT}：后者是 MySQL 方言，
     * PostgreSQL 不支持 DELETE 上直接使用 ORDER BY/LIMIT，两步写法在双方言下语义一致。
     *
     * @param tenantId 租户标识
     * @param before   完成时间阈值，早于该时刻的任务视为过期
     * @param limit    单批条数上限
     * @return 过期终态任务主键，按完成时间升序
     */
    @Select("SELECT id FROM gen_task "
            + "WHERE tenant_id=#{tenantId} "
            + "AND status IN ('COMPLETED','FAILED','CANCELED') AND completed_at < #{before} "
            + "ORDER BY completed_at ASC, id ASC LIMIT #{limit}")
    List<Long> findFinishedIdsBefore(
            @Param("tenantId") String tenantId, @Param("before") LocalDateTime before, @Param("limit") int limit);

    /**
     * 按主键物理删除终态任务。
     *
     * <p>任务表继承 {@code BasePO} 的逻辑删除，MyBatis-Plus 的 {@code deleteByIds} 只会
     * 打删除标记、记录仍留在表内，大字段无法回收，因此这里使用显式物理 DELETE。
     * 调用方传入的主键集合来自 {@link #findFinishedIdsBefore}，限定终态与租户语义由
     * 查询步骤保证；本语句按主键删除并重复携带终态条件兜底。
     *
     * @param ids 待删除的任务主键集合，非空
     * @return 实际删除条数
     */
    @Delete("<script>DELETE FROM gen_task WHERE status IN ('COMPLETED','FAILED','CANCELED') "
            + "AND id IN <foreach collection='ids' item='itemId' open='(' separator=',' close=')'>"
            + "#{itemId}</foreach></script>")
    int deleteFinishedByIds(@Param("ids") List<Long> ids);
}
