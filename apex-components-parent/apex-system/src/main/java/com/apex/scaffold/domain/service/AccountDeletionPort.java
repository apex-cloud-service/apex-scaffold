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

    /**
     * 停用账号并入队清理任务。
     *
     * @param command 注销命令
     * @return 已受理的任务信息
     */
    Mono<AcceptedDeletion> deactivateAndEnqueue(DeletionCommand command);

    /**
     * 管理端注销账号并入队清理任务。
     *
     * <p>管理端注销允许目标用户已处于禁用状态，但仍要求身份字段与锁定记录完全一致。</p>
     *
     * @param command 注销命令
     * @return 已受理的任务信息
     */
    default Mono<AcceptedDeletion> deactivateAndEnqueueByAdmin(DeletionCommand command) {
        return deactivateAndEnqueue(command);
    }

    /**
     * 执行账号数据清理。
     *
     * @param task 清理任务
     * @return 操作完成信号，失败时传播错误
     */
    Mono<Void> cleanup(DeletionTask task);

    /**
     * 注销请求的同步输入。
     *
     * @param userId 待注销用户标识
     * @param openId 微信 OpenID，用于定位第三方绑定
     * @param phone 手机号，用于定位账号
     * @param requestId 注销请求标识，用于幂等
     * @param clientIp 发起请求的客户端 IP
     * @param traceId 链路追踪标识
     * @author Apex Team
     * @version 1.0.0
     * @since 1.0.0
     */
    record DeletionCommand(
            Long userId, String openId, String phone, String requestId, String clientIp, String traceId) {}

    /**
     * 同步注销完成后返回的任务标识。
     *
     * @param jobId 异步清理任务标识
     * @param requestId 注销请求标识，用于幂等
     * @param status 受理状态
     * @author Apex Team
     * @version 1.0.0
     * @since 1.0.0
     */
    record AcceptedDeletion(Long jobId, String requestId, String status) {}

    /**
     * 异步清理任务快照。
     *
     * @param jobId 清理任务标识
     * @param userId 待清理的用户标识
     * @author Apex Team
     * @version 1.0.0
     * @since 1.0.0
     */
    record DeletionTask(Long jobId, Long userId) {}
}
