package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.TaskPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Task MyBatis Mapper 接口。
 *
 * <p>基于 MyBatis-Plus BaseMapper，提供 Task 的基础 CRUD 操作。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
/**n * TaskMappern *n * <p>TODO: 补充 Mapper 的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public interface TaskMapper extends BaseMapper<TaskPO> {

    @Select("SELECT COUNT(*) FROM wf_task WHERE status = 'PENDING' AND deleted = 0")
    Integer countPendingTasks();

    @Select(
            "SELECT COUNT(*) FROM wf_task WHERE status = 'PENDING' AND timeout_at IS NOT NULL AND timeout_at <= #{now} AND timeout_handled = 0 AND deleted = 0")
    Integer countTimeoutTasks(@Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM wf_task WHERE transferred_from IS NOT NULL AND deleted = 0")
    Integer countTransferredTasks();
}
