package com.apex.scaffold.infrastructure.persistence.service;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.service.AccountDeletionPort;
import com.apex.scaffold.infrastructure.persistence.mapper.AccountDeletionCleanupMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.AccountDeletionTaskMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.UserMapper;
import com.apex.scaffold.infrastructure.persistence.po.AccountDeletionTaskPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 账号注销端口实现（基础设施层 Adapter）。
 *
 * <p>实现 {@link AccountDeletionPort} 契约：
 * <ul>
 *   <li>{@link #deactivateAndEnqueue} — 让账号立即失效（{@code sys_user.status=0}）
 *       并写入 {@code sys_account_deletion_task} 清理任务（PENDING），供
 *       {@code AccountDeletionTaskScheduler} 异步消费；</li>
 *   <li>{@link #cleanup} — 按 {@link AccountDeletionCleanupMapper} 的通用清理 SQL
 *       删除用户关联数据，最后逻辑删除用户主记录。</li>
 * </ul>
 * 所有 JDBC 阻塞调用均隔离到 {@link BlockingIoSchedulers#jdbc()} 专用线程池。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionPortAdapter implements AccountDeletionPort {

    /** 注销任务 Mapper（读写 sys_account_deletion_task）。 */
    private final AccountDeletionTaskMapper taskMapper;

    /** 用户数据清理 SQL（通用业务表）。 */
    private final AccountDeletionCleanupMapper cleanupMapper;

    /** 用户 Mapper（账号失效与主记录删除）。 */
    private final UserMapper userMapper;

    /** 任务初始状态。 */
    private static final String STATUS_PENDING = "PENDING";

    /**
     * 让账号立即失效并写入清理任务。
     *
     * <p>账号已处于禁用状态或不存在时返回空 {@link Mono}，由调用方按
     * 「注销已完成」处理（{@code ACCOUNT_DELETION_ALREADY_COMPLETED}）。</p>
     *
     * @param command 注销命令
     * @return 受理任务信息；账号已失效/不存在时为空
     */
    @Override
    public Mono<AcceptedDeletion> deactivateAndEnqueue(DeletionCommand command) {
        return Mono.fromCallable(() -> {
                    // 1. 账号立即失效（status 1 → 0），失败说明已禁用或不存在
                    int disabled = userMapper.deactivateForAccountDeletion(command.userId());
                    if (disabled == 0) {
                        log.info(
                                "账号注销：用户已处于禁用状态或不存在，视为已完成，userId={}, requestId={}",
                                command.userId(),
                                command.requestId());
                        return null;
                    }
                    // 2. 写入异步清理任务
                    AccountDeletionTaskPO po = new AccountDeletionTaskPO();
                    po.setUserId(command.userId());
                    po.setRequestId(command.requestId());
                    po.setStatus(STATUS_PENDING);
                    po.setAttempts(0);
                    taskMapper.insert(po);
                    log.info(
                            "账号注销：任务已入队，jobId={}, userId={}, requestId={}",
                            po.getId(),
                            command.userId(),
                            command.requestId());
                    return new AcceptedDeletion(po.getId(), command.requestId(), STATUS_PENDING);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 清理已注销用户的关联数据并删除主记录。
     *
     * @param task 清理任务快照
     */
    @Override
    public Mono<Void> cleanup(DeletionTask task) {
        return Mono.fromRunnable(() -> {
                    Long userId = task.userId();
                    cleanupMapper.deleteUserRoles(userId);
                    cleanupMapper.deleteUserProfile(userId);
                    cleanupMapper.deleteUserCompliance(userId);
                    cleanupMapper.deleteUserConsent(userId);
                    cleanupMapper.deleteBlacklist(userId);
                    cleanupMapper.deleteCreditTransactions(userId);
                    cleanupMapper.deleteCreditAccount(userId);
                    cleanupMapper.deleteSystemMessages(userId);
                    cleanupMapper.deleteSamples(userId);
                    cleanupMapper.deleteFilesExceptReportEvidence(userId);
                    // 最后逻辑删除用户主记录（MyBatis-Plus 逻辑删除 → deleted=1）
                    userMapper.deleteById(userId);
                    log.info("账号注销：用户数据清理完成，userId={}", userId);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .then();
    }
}
