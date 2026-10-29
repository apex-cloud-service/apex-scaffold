package com.apex.scaffold.workflow.domain.repository;

import com.apex.scaffold.workflow.domain.model.Task;
import reactor.core.publisher.Flux;

/**
 * 后台超时扫描端口。
 *
 * <p>仅供超时调度使用：跨租户读取已到期且仍待处理的任务快照，返回的任务携带其持久租户归属，
 * 由调度器逐任务切换租户上下文后处理。不得作为前台任务查询入口。</p>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface TimeoutTaskScan {

    /**
     * 查询所有租户中已到期且仍待处理的任务。
     *
     * @return 到期任务快照，任务对象包含持久租户标识
     */
    Flux<Task> findPendingTasks();
}
