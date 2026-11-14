package com.apex.scaffold.workflow.infrastructure.persistence.mapper;

import com.apex.scaffold.workflow.infrastructure.persistence.po.TaskPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 流程任务 MyBatis Mapper 接口。
 *
 * <p>基于 MyBatis-Plus {@link BaseMapper}，提供流程任务持久化对象
 * {@link TaskPO} 的基础 CRUD 操作，并额外提供用于超时处理与监控统计的计数查询。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface TaskMapper extends BaseMapper<TaskPO> {

    /**
     * 统计指定租户处于待处理（PENDING）状态的任务数量。
     *
     * @param tenant 租户标识，来自可信认证上下文
     * @return 待处理任务数量
     */
    @Select("SELECT COUNT(*) FROM wf_task WHERE tenant_id = #{tenant} AND status = 'PENDING' AND deleted = 0")
    Integer countPendingTasks(@Param("tenant") String tenant);

    /**
     * 统计指定租户截至指定时刻已超时且尚未处理的任务数量。
     *
     * @param tenant 租户标识，来自可信认证上下文
     * @param now 当前时刻
     * @return 超时未处理任务数量
     */
    @Select("SELECT COUNT(*) FROM wf_task WHERE tenant_id = #{tenant} AND status = 'PENDING'"
            + " AND timeout_at IS NOT NULL AND timeout_at <= #{now} AND timeout_handled = 0 AND deleted = 0")
    Integer countTimeoutTasks(@Param("tenant") String tenant, @Param("now") LocalDateTime now);

    /**
     * 统计指定租户发生转办的任务数量。
     *
     * @param tenant 租户标识，来自可信认证上下文
     * @return 转办任务数量
     */
    @Select("SELECT COUNT(*) FROM wf_task WHERE tenant_id = #{tenant} AND transferred_from IS NOT NULL AND deleted = 0")
    Integer countTransferredTasks(@Param("tenant") String tenant);
}
