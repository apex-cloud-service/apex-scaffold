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
     * <p>可认领包含两类：待处理且未超过最大尝试次数的任务，以及认领后长时间
     * 未更新的 RUNNING 任务（实例崩溃遗留，允许其他实例接管）。
     *
     * @param tenantId      租户标识
     * @param maxAttempts   最大尝试次数
     * @param leaseDeadline 认领租约过期阈值
     * @return 候选任务；无候选返回 {@code null}
     */
    @Select("SELECT * FROM gen_task "
            + "WHERE deleted=0 AND tenant_id=#{tenantId} "
            + "AND ((status='PENDING' AND attempts < #{maxAttempts}) "
            + "OR (status='RUNNING' AND locked_at < #{leaseDeadline})) "
            + "ORDER BY create_time ASC, id ASC LIMIT 1")
    GenTaskPO findNextCandidate(
            @Param("tenantId") String tenantId,
            @Param("maxAttempts") int maxAttempts,
            @Param("leaseDeadline") LocalDateTime leaseDeadline);

    /**
     * 列出当前存在可认领任务的租户。
     *
     * <p>生成任务由用户提交，租户集合不确定，调度器无法像体检快照导出那样固定读取
     * 平台租户配置。这里先取有候选任务的租户列表，再逐租户串行认领，避免全表扫描
     * 到内存后逐条校验。
     *
     * @param maxAttempts   最大尝试次数
     * @param leaseDeadline 认领租约过期阈值
     * @param limit         单轮最多返回的租户数
     * @return 候选租户标识
     */
    @Select("SELECT DISTINCT tenant_id FROM gen_task "
            + "WHERE deleted=0 "
            + "AND ((status='PENDING' AND attempts < #{maxAttempts}) "
            + "OR (status='RUNNING' AND locked_at < #{leaseDeadline})) "
            + "ORDER BY tenant_id LIMIT #{limit}")
    List<String> findCandidateTenants(
            @Param("maxAttempts") int maxAttempts,
            @Param("leaseDeadline") LocalDateTime leaseDeadline,
            @Param("limit") int limit);

    /**
     * 列出当前存在过期终态任务的租户。
     *
     * <p>保留期清理与认领调度是两类完全不同的候选集合：可认领租户只包含 PENDING 或
     * 租约过期的 RUNNING，终态任务所在租户在其中不会出现，因此清理需要单独的租户发现。
     *
     * @param before 终态时间阈值，早于该时刻结束的任务视为过期
     * @param limit  单轮最多返回的租户数
     * @return 存在过期终态任务的租户标识
     */
    @Select("SELECT DISTINCT tenant_id FROM gen_task "
            + "WHERE deleted=0 AND status IN ('COMPLETED','FAILED','CANCELED') "
            + "AND completed_at < #{before} "
            + "ORDER BY tenant_id LIMIT #{limit}")
    List<String> findFinishedTenants(@Param("before") LocalDateTime before, @Param("limit") int limit);

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
     * 回写处理进度。
     *
     * @param id             任务标识
     * @param tenantId       租户标识
     * @param totalCount     总处理单元数
     * @param processedCount 已处理单元数
     * @param failedCount    失败单元数
     * @param errorMessage   最后一次单元错误摘要
     * @return 影响行数
     */
    @Update("UPDATE gen_task SET total_count=#{totalCount}, processed_count=#{processedCount}, "
            + "failed_count=#{failedCount}, error_message=#{errorMessage}, update_time=NOW() "
            + "WHERE id=#{id} AND deleted=0 AND tenant_id=#{tenantId} AND status='RUNNING'")
    int updateProgress(
            @Param("id") Long id,
            @Param("tenantId") String tenantId,
            @Param("totalCount") int totalCount,
            @Param("processedCount") int processedCount,
            @Param("failedCount") int failedCount,
            @Param("errorMessage") String errorMessage);

    /**
     * 标记任务完成并回写生成历史。
     *
     * @param id             任务标识
     * @param tenantId       租户标识
     * @param historyId      生成历史 ID
     * @param totalCount     总处理单元数
     * @param processedCount 已处理单元数
     * @param failedCount    失败单元数
     * @param completedAt    完成时间
     * @return 影响行数
     */
    @Update("UPDATE gen_task SET status='COMPLETED', history_id=#{historyId}, "
            + "total_count=#{totalCount}, processed_count=#{processedCount}, failed_count=#{failedCount}, "
            + "completed_at=#{completedAt}, locked_at=NULL, locked_by=NULL, update_time=NOW() "
            + "WHERE id=#{id} AND deleted=0 AND tenant_id=#{tenantId} AND status='RUNNING'")
    int markCompleted(
            @Param("id") Long id,
            @Param("tenantId") String tenantId,
            @Param("historyId") Long historyId,
            @Param("totalCount") int totalCount,
            @Param("processedCount") int processedCount,
            @Param("failedCount") int failedCount,
            @Param("completedAt") LocalDateTime completedAt);

    /**
     * 标记任务失败，保留已累加的尝试次数供后续重试判断。
     *
     * @param id          任务标识
     * @param tenantId    租户标识
     * @param errorMessage 失败原因（已截断）
     * @param completedAt 完成时间
     * @return 影响行数
     */
    @Update("UPDATE gen_task SET status='FAILED', error_message=#{errorMessage}, "
            + "completed_at=#{completedAt}, locked_at=NULL, locked_by=NULL, update_time=NOW() "
            + "WHERE id=#{id} AND deleted=0 AND tenant_id=#{tenantId} AND status='RUNNING'")
    int markFailed(
            @Param("id") Long id,
            @Param("tenantId") String tenantId,
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
     * 物理清理已过保留期的终态任务。
     *
     * <p>任务表继承 {@code BasePO} 的逻辑删除，{@code deleteById} 只会打删除标记，
     * 记录仍留在表内，因此这里使用显式 DELETE。</p>
     *
     * @param tenantId 租户标识
     * @param before   完成时间阈值，早于该时刻的任务视为过期
     * @param limit    单次清理条数
     * @return 实际删除条数
     */
    @Delete("DELETE FROM gen_task WHERE tenant_id=#{tenantId} "
            + "AND status IN ('COMPLETED','FAILED','CANCELED') AND completed_at < #{before} "
            + "ORDER BY completed_at LIMIT #{limit}")
    int purgeFinishedBefore(
            @Param("tenantId") String tenantId, @Param("before") LocalDateTime before, @Param("limit") int limit);
}
