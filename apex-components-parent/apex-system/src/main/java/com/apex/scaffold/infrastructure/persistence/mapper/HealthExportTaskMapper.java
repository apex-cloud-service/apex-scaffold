package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.HealthExportTaskPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 体检快照异步导出任务 Mapper。
 *
 * <p>导出任务属于租户级数据，候选查询、认领与状态回写全部带 {@code tenant_id} 条件，
 * 因为以下 SQL 是注解手写，不会经过持久化拦截器的自动租户追加。</p>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Mapper
public interface HealthExportTaskMapper extends BaseMapper<HealthExportTaskPO> {

    /**
     * 查找一条可认领的导出任务。
     *
     * <p>可认领包含两类：待处理/失败且未超过最大尝试次数的任务，以及认领后长时间
     * 未更新的 RUNNING 任务（实例崩溃遗留，允许其他实例接管）。</p>
     *
     * @param tenantId    租户标识
     * @param maxAttempts 最大尝试次数
     * @param staleBefore 认领租约过期阈值
     * @return 候选任务；无候选返回 {@code null}
     */
    @Select("SELECT * FROM ops_health_export_task "
            + "WHERE deleted=0 AND tenant_id=#{tenantId} "
            + "AND ((status IN ('PENDING','FAILED') AND attempts < #{maxAttempts}) "
            + "OR (status='RUNNING' AND locked_at < #{staleBefore})) "
            + "ORDER BY id LIMIT 1")
    HealthExportTaskPO findNextCandidate(
            @Param("tenantId") String tenantId,
            @Param("maxAttempts") int maxAttempts,
            @Param("staleBefore") LocalDateTime staleBefore);

    /**
     * 竞争认领任务。
     *
     * @param id          任务标识
     * @param tenantId    租户标识
     * @param now         认领时间
     * @param workerId    认领实例标识
     * @param staleBefore 认领租约过期阈值
     * @return 影响行数（1=认领成功，0=已被其他实例抢先）
     */
    @Update("UPDATE ops_health_export_task SET status='RUNNING', attempts=attempts+1, "
            + "locked_at=#{now}, locked_by=#{workerId}, update_time=NOW() "
            + "WHERE id=#{id} AND deleted=0 AND tenant_id=#{tenantId} "
            + "AND (status IN ('PENDING','FAILED') OR (status='RUNNING' AND locked_at < #{staleBefore}))")
    int claim(
            @Param("id") Long id,
            @Param("tenantId") String tenantId,
            @Param("now") LocalDateTime now,
            @Param("workerId") String workerId,
            @Param("staleBefore") LocalDateTime staleBefore);

    /**
     * 标记导出完成并落库生成的文件。
     *
     * @param id       任务标识
     * @param tenantId 租户标识
     * @param fileName 文件名
     * @param rowCount 记录数
     * @param content  文件内容
     * @param now      完成时间
     * @return 影响行数
     */
    @Update("UPDATE ops_health_export_task SET status='COMPLETED', file_name=#{fileName}, "
            + "row_count=#{rowCount}, content=#{content}, completed_at=#{now}, last_error=NULL, "
            + "locked_at=NULL, locked_by=NULL, update_time=NOW() "
            + "WHERE id=#{id} AND deleted=0 AND tenant_id=#{tenantId}")
    int markCompleted(
            @Param("id") Long id,
            @Param("tenantId") String tenantId,
            @Param("fileName") String fileName,
            @Param("rowCount") int rowCount,
            @Param("content") byte[] content,
            @Param("now") LocalDateTime now);

    /**
     * 标记导出失败，保留已累加的尝试次数供后续重试判断。
     *
     * @param id       任务标识
     * @param tenantId 租户标识
     * @param error    失败原因（已截断）
     * @return 影响行数
     */
    @Update("UPDATE ops_health_export_task SET status='FAILED', last_error=#{error}, "
            + "locked_at=NULL, locked_by=NULL, update_time=NOW() "
            + "WHERE id=#{id} AND deleted=0 AND tenant_id=#{tenantId}")
    int markFailed(@Param("id") Long id, @Param("tenantId") String tenantId, @Param("error") String error);

    /**
     * 物理清理已过保留期的导出结果，回收 {@code content} 占用的存储。
     *
     * <p>任务表继承 {@code BasePO} 的逻辑删除，{@code deleteById} 只会打删除标记，
     * 大字段仍留在表内，因此这里使用显式 DELETE。</p>
     *
     * @param tenantId 租户标识
     * @param before   完成时间阈值，早于该时刻的任务视为过期
     * @param limit    单次清理条数
     * @return 实际删除条数
     */
    @Delete("DELETE FROM ops_health_export_task "
            + "WHERE tenant_id=#{tenantId} AND status='COMPLETED' AND completed_at < #{before} "
            + "ORDER BY completed_at LIMIT #{limit}")
    int purgeCompletedBefore(
            @Param("tenantId") String tenantId, @Param("before") LocalDateTime before, @Param("limit") int limit);
}
