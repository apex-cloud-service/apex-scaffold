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
     * 统计指定时间区间内发起的流程实例数量。
     *
     * @param start 区间起点（含）
     * @param end 区间终点（不含）
     * @return 发起的流程实例数量
     */
    @Select(
            "SELECT COUNT(*) FROM wf_process_instance WHERE start_time >= #{start} AND start_time < #{end} AND deleted = 0")
    Integer countTodayStarted(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 按状态统计流程实例数量。
     *
     * @param status 流程实例状态
     * @return 指定状态的流程实例数量
     */
    @Select("SELECT COUNT(*) FROM wf_process_instance WHERE status = #{status} AND deleted = 0")
    Integer countByStatus(@Param("status") String status);

    /**
     * 统计处于挂起状态的流程实例数量。
     *
     * @return 挂起的流程实例数量
     */
    @Select("SELECT COUNT(*) FROM wf_process_instance WHERE suspended = 1 AND deleted = 0")
    Integer countSuspended();

    /**
     * 统计已结束流程实例的平均处理耗时（秒）。
     *
     * @return 平均耗时（秒）；无已结束实例时可能为 {@code null}
     */
    @Select(
            "SELECT AVG(TIMESTAMPDIFF(SECOND, start_time, end_time)) FROM wf_process_instance WHERE end_time IS NOT NULL AND deleted = 0")
    Long avgCompletionSeconds();

    /**
     * 按状态统计流程实例分布。
     *
     * @return 由状态与数量组成的记录列表，每项包含 {@code status} 与 {@code count}
     */
    @Select("SELECT status, COUNT(*) as count FROM wf_process_instance WHERE deleted = 0 GROUP BY status")
    List<Map<String, Object>> statusDistribution();

    /**
     * 按日期区间统计每日发起的流程实例数量。
     *
     * @param start 区间起点（含）
     * @param end 区间终点（不含）
     * @return 由日期与数量组成的记录列表，每项包含 {@code date} 与 {@code count}
     */
    @Select(
            "SELECT DATE(start_time) as date, COUNT(*) as count FROM wf_process_instance WHERE start_time >= #{start} AND start_time < #{end} AND deleted = 0 GROUP BY DATE(start_time)")
    List<Map<String, Object>> countStartedByDateRange(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 按日期区间统计每日完成的流程实例数量。
     *
     * @param start 区间起点（含）
     * @param end 区间终点（不含）
     * @return 由日期与数量组成的记录列表，每项包含 {@code date} 与 {@code count}
     */
    @Select(
            "SELECT DATE(end_time) as date, COUNT(*) as count FROM wf_process_instance WHERE end_time >= #{start} AND end_time < #{end} AND deleted = 0 GROUP BY DATE(end_time)")
    List<Map<String, Object>> countCompletedByDateRange(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}