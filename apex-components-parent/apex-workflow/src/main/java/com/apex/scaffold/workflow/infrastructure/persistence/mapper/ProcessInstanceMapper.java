package com.apex.scaffold.workflow.infrastructure.persistence.mapper;

import com.apex.scaffold.workflow.infrastructure.persistence.po.ProcessInstancePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 流程实例 MyBatis Mapper 接口。
 *
 * <p>基于 MyBatis-Plus {@link BaseMapper}，提供流程实例持久化对象
 * {@link ProcessInstancePO} 的基础 CRUD 操作，并额外提供用于监控统计的聚合查询。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface ProcessInstanceMapper extends BaseMapper<ProcessInstancePO> {

    /**
     * 统计指定租户在时间区间内发起的流程实例数量。
     *
     * @param tenant 租户标识，来自可信认证上下文
     * @param start 区间起点（含）
     * @param end 区间终点（不含）
     * @return 发起的流程实例数量
     */
    @Select("SELECT COUNT(*) FROM wf_process_instance WHERE tenant_id = #{tenant}"
            + " AND start_time >= #{start} AND start_time < #{end} AND deleted = 0")
    Integer countTodayStarted(
            @Param("tenant") String tenant, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 按状态统计指定租户的流程实例数量。
     *
     * @param tenant 租户标识，来自可信认证上下文
     * @param status 流程实例状态
     * @return 指定状态的流程实例数量
     */
    @Select(
            "SELECT COUNT(*) FROM wf_process_instance WHERE tenant_id = #{tenant} AND status = #{status} AND deleted = 0")
    Integer countByStatus(@Param("tenant") String tenant, @Param("status") String status);

    /**
     * 统计指定租户处于挂起状态的流程实例数量。
     *
     * @param tenant 租户标识，来自可信认证上下文
     * @return 挂起的流程实例数量
     */
    @Select("SELECT COUNT(*) FROM wf_process_instance WHERE tenant_id = #{tenant} AND suspended = 1 AND deleted = 0")
    Integer countSuspended(@Param("tenant") String tenant);

    /**
     * 统计指定租户已结束流程实例的平均处理耗时（秒）。
     *
     * @param tenant 租户标识，来自可信认证上下文
     * @return 平均耗时（秒）；无已结束实例时可能为 {@code null}
     */
    @Select("<script>SELECT AVG(<choose>"
            + "<when test=\"_databaseId == 'postgresql'\">TRUNC(EXTRACT(EPOCH FROM (end_time - start_time)))</when>"
            + "<otherwise>TIMESTAMPDIFF(SECOND, start_time, end_time)</otherwise>"
            + "</choose>) FROM wf_process_instance"
            + " WHERE tenant_id = #{tenant} AND end_time IS NOT NULL AND deleted = 0</script>")
    Long avgCompletionSeconds(@Param("tenant") String tenant);

    /**
     * 按状态统计指定租户的流程实例分布。
     *
     * @param tenant 租户标识，来自可信认证上下文
     * @return 由状态与数量组成的记录列表，每项包含 {@code status} 与 {@code count}
     */
    @Select("SELECT status, COUNT(*) as count FROM wf_process_instance WHERE tenant_id = #{tenant} AND deleted = 0"
            + " GROUP BY status")
    List<Map<String, Object>> statusDistribution(@Param("tenant") String tenant);

    /**
     * 按日期区间统计指定租户每日发起的流程实例数量。
     *
     * @param tenant 租户标识，来自可信认证上下文
     * @param start 区间起点（含）
     * @param end 区间终点（不含）
     * @return 由日期与数量组成的记录列表，每项包含 {@code date} 与 {@code count}
     */
    @Select("SELECT DATE(start_time) as date, COUNT(*) as count FROM wf_process_instance"
            + " WHERE tenant_id = #{tenant} AND start_time >= #{start} AND start_time < #{end} AND deleted = 0"
            + " GROUP BY DATE(start_time)")
    List<Map<String, Object>> countStartedByDateRange(
            @Param("tenant") String tenant, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 按日期区间统计指定租户每日完成的流程实例数量。
     *
     * @param tenant 租户标识，来自可信认证上下文
     * @param start 区间起点（含）
     * @param end 区间终点（不含）
     * @return 由日期与数量组成的记录列表，每项包含 {@code date} 与 {@code count}
     */
    @Select("SELECT DATE(end_time) as date, COUNT(*) as count FROM wf_process_instance"
            + " WHERE tenant_id = #{tenant} AND end_time >= #{start} AND end_time < #{end} AND deleted = 0"
            + " GROUP BY DATE(end_time)")
    List<Map<String, Object>> countCompletedByDateRange(
            @Param("tenant") String tenant, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
