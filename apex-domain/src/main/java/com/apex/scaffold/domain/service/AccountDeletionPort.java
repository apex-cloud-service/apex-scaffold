package com.apex.scaffold.domain.service;

import reactor.core.publisher.Mono;

/**
 * 账号注销持久化端口。
 *
 * <p>同步方法只负责让账号立即失效、释放可重注册身份并写入清理任务；
 * 具体的用户数据清理由异步任务执行。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface AccountDeletionPort {

    /** @param command 注销命令 @return 已受理的任务信息 */
    Mono<AcceptedDeletion> deactivateAndEnqueue(DeletionCommand command);

    /** 管理端注销允许目标用户已处于禁用状态，但仍要求身份字段与锁定记录完全一致。 */
    default Mono<AcceptedDeletion> deactivateAndEnqueueByAdmin(DeletionCommand command) {
        return deactivateAndEnqueue(command);
    }

    /** @param task 清理任务 @return 清理完成信号 */
    Mono<Void> cleanup(DeletionTask task);

    /**
     * 注销请求的同步输入。
     *
     * @author Apex Team
     * @version 1.0.0
     * @since 1.0.0
     */
    record DeletionCommand(
            Long userId, String openId, String phone, String requestId, String clientIp, String traceId) {}

    /**
     * 同步注销完成后返回的任务标识。
     *
     * @author Apex Team
     * @version 1.0.0
     * @since 1.0.0
     */
    record AcceptedDeletion(Long jobId, String requestId, String status) {}

    /**
     * 异步清理任务快照。
     *
     * @author Apex Team
     * @version 1.0.0
     * @since 1.0.0
     */
    record DeletionTask(Long jobId, Long userId) {}
}
