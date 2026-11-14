package com.apex.scaffold.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 特权审核状态迁移的只追加审计写入器。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface AdminReviewAuditMapper {

    /**
     * 追加一条特权审核状态变更审计记录。
     *
     * <p>本表只追加不更新，用于还原管理员对审核聚合的关键状态迁移；
     * {@code beforeState} 与 {@code afterState} 记录变更前后的状态快照，
     * 供审计追溯与问题定位。</p>
     *
     * @param id           审计记录主键（由调用方生成，保证幂等）
     * @param aggregateType 被审计聚合类型（如 {@code PhotoReport}、{@code ModerationTask}）
     * @param aggregateId   被审计聚合实例 ID
     * @param action        触发审计的动作标识
     * @param actorId       执行操作的管理员用户 ID
     * @param beforeState   变更前状态快照（可含 JSON 细节）
     * @param afterState    变更后状态快照（可含 JSON 细节）
     * @param reasonCode    业务原因编码，无原因时可传 {@code null}
     * @param comment       操作备注，可为 {@code null}
     * @param traceId       链路追踪 ID，可用于串联一次操作的全部分支
     * @return 受影响行数，成功插入时为 1
     */
    @Insert("INSERT INTO biz_admin_review_audit "
            + "(id,aggregate_type,aggregate_id,action,actor_id,before_state,after_state,reason_code,comment,trace_id) "
            + "VALUES (#{id},#{aggregateType},#{aggregateId},#{action},#{actorId},#{beforeState},#{afterState},"
            + "#{reasonCode},#{comment},#{traceId})")
    int append(
            @Param("id") long id,
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") String aggregateId,
            @Param("action") String action,
            @Param("actorId") String actorId,
            @Param("beforeState") String beforeState,
            @Param("afterState") String afterState,
            @Param("reasonCode") String reasonCode,
            @Param("comment") String comment,
            @Param("traceId") String traceId);
}
