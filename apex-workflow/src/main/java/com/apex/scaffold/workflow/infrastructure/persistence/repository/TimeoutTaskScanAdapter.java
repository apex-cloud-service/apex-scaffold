package com.apex.scaffold.workflow.infrastructure.persistence.repository;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.workflow.domain.model.Task;
import com.apex.scaffold.workflow.domain.repository.TimeoutTaskScan;
import com.apex.scaffold.workflow.infrastructure.persistence.converter.TaskPoConverter;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.TaskMapper;
import com.apex.scaffold.workflow.infrastructure.persistence.po.TaskPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 后台调度专用扫描适配器；不得作为前台任务查询入口。
 *
 * <p>跨租户读取到期任务快照，租户归属由任务持久字段提供，调用方负责逐任务切换租户上下文。</p>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Component
@RequiredArgsConstructor
public class TimeoutTaskScanAdapter implements TimeoutTaskScan {
    private final TaskMapper taskMapper;
    private final TaskPoConverter converter;

    /** {@inheritDoc} */
    @Override
    public Flux<Task> findPendingTasks() {
        return Mono.fromCallable(() -> taskMapper.selectList(ConditionUtil.lambda(TaskPO.class)
                        .eq(TaskPO::getStatus, "PENDING")
                        .isNotNull(TaskPO::getTimeoutAt)
                        .le(TaskPO::getTimeoutAt, java.time.LocalDateTime.now())))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapIterable(converter::toEntityList);
    }
}
