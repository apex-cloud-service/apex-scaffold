package com.apex.scaffold.domain.workflow.repository;

import com.apex.scaffold.domain.workflow.ApprovalRecordEntity;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 审批记录仓储接口。
 *
 * <p>定义审批记录的持久化操作契约，支持按流程实例、任务等维度查询审批历史。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface ApprovalRecordRepository {

    Mono<ApprovalRecordEntity> findById(Long id);

    Mono<List<ApprovalRecordEntity>> findByProcessInstanceId(Long processInstanceId);

    Mono<List<ApprovalRecordEntity>> findByTaskId(Long taskId);

    Mono<Integer> insert(ApprovalRecordEntity entity);

    Mono<Integer> deleteById(Long id);
}
