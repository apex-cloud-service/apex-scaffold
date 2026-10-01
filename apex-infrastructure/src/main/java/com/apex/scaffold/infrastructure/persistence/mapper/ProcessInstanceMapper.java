package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.ProcessInstancePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * ProcessInstance MyBatis Mapper 接口。
 *
 * <p>基于 MyBatis-Plus BaseMapper，提供 ProcessInstance 的基础 CRUD 操作。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface ProcessInstanceMapper extends BaseMapper<ProcessInstancePO> {

    @Select(
            "SELECT COUNT(*) FROM wf_process_instance WHERE start_time >= #{start} AND start_time < #{end} AND deleted = 0")
    /**
     * TODO: Add method description
     *
     * @param start TODO
     * @param end TODO
     * @return TODO
     */
    Integer countTodayStarted(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) FROM wf_process_instance WHERE status = #{status} AND deleted = 0")
    /**
     * TODO: Add method description
     *
     * @param status TODO
     * @return TODO
     */
    Integer countByStatus(@Param("status") String status);

    @Select("SELECT COUNT(*) FROM wf_process_instance WHERE suspended = 1 AND deleted = 0")
    /**
     * TODO: Add method description
     * @return TODO
     */
    Integer countSuspended();

    @Select(
            "SELECT AVG(TIMESTAMPDIFF(SECOND, start_time, end_time)) FROM wf_process_instance WHERE end_time IS NOT NULL AND deleted = 0")
    /**
     * TODO: Add method description
     * @return TODO
     */
    Long avgCompletionSeconds();

    @Select("SELECT status, COUNT(*) as count FROM wf_process_instance WHERE deleted = 0 GROUP BY status")
    /**
     * TODO: Add method description
     * @return TODO
     */
    List<Map<String, Object>> statusDistribution();

    @Select(
            "SELECT DATE(start_time) as date, COUNT(*) as count FROM wf_process_instance WHERE start_time >= #{start} AND start_time < #{end} AND deleted = 0 GROUP BY DATE(start_time)")
    /**
     * TODO: Add method description
     *
     * @param start TODO
     * @param end TODO
     * @return TODO
     */
    List<Map<String, Object>> countStartedByDateRange(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select(
            "SELECT DATE(end_time) as date, COUNT(*) as count FROM wf_process_instance WHERE end_time >= #{start} AND end_time < #{end} AND deleted = 0 GROUP BY DATE(end_time)")
    /**
     * TODO: Add method description
     *
     * @param start TODO
     * @param end TODO
     * @return TODO
     */
    List<Map<String, Object>> countCompletedByDateRange(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
