package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.workflow.ApprovalRecord;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 审批记录仓储接口。
 *
 * <p>定义审批记录的持久化操作契约，由基础设施层实现。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface ApprovalRecordRepository {

    Mono<ApprovalRecord> findById(Long id);

    Mono<List<ApprovalRecord>> findByProcessInstanceId(Long processInstanceId);

    Mono<List<ApprovalRecord>> findByTaskId(Long taskId);

    Mono<Integer> insert(ApprovalRecord entity);

    Mono<Integer> updateById(ApprovalRecord entity);

    Mono<Integer> deleteById(Long id);
}
