package com.apex.scaffold.infrastructure.scheduler;

import static com.apex.scaffold.common.scheduler.BlockingIoSchedulers.jdbc;

import com.apex.scaffold.domain.service.AccountDeletionPort;
import com.apex.scaffold.infrastructure.persistence.mapper.AccountDeletionTaskMapper;
import com.apex.scaffold.infrastructure.persistence.po.AccountDeletionTaskPO;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 账号注销异步清理任务调度器。
 *
 * <p>周期性扫描 {@code sys_account_deletion_task} 表，领取待处理的注销任务，
 * 通过 {@link AccountDeletionPort} 执行用户数据清理，并将结果回写任务表。
 * 所有 JDBC 阻塞调用均迁移至通用阻塞 I/O 专用 {@code jdbc()} 池，
 * 避免在 Spring 调度线程（单线程）上直接执行阻塞 SQL。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountDeletionTaskScheduler {

    /** 本调度器实例唯一标识，用于并发任务认领防冲突。 */
    private static final String WORKER_ID = "account-delete-" + UUID.randomUUID();

    /** 待办任务查询与认领 Mapper。 */
    private final AccountDeletionTaskMapper taskMapper;

    /** 账号注销领域能力端口，执行具体的数据清理业务逻辑。 */
    private final AccountDeletionPort accountDeletionPort;

    /**
     * 定时扫描并领取一条注销任务。
     *
     * <p>每轮最多领取一条，避免长时间占用调度线程；领取后异步调用
     * {@link AccountDeletionPort#cleanup} 完成业务清理，
     * 成功/失败均通过 Mapper 回写任务状态。
     * 间隔默认 5 秒，可通过 {@code apex.account-deletion.poll-delay-ms} 配置覆盖。</p>
     */
    @Scheduled(fixedDelayString = "${apex.account-deletion.poll-delay-ms:5000}")
    public void poll() {
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(10);
        Mono.fromCallable(() -> taskMapper.findNextCandidate(staleBefore))
                .subscribeOn(jdbc())
                .flatMap(candidate -> claim(candidate, staleBefore).flatMap(claimed -> accountDeletionPort
                        .cleanup(new AccountDeletionPort.DeletionTask(claimed.getId(), claimed.getUserId()))
                        .then(markCompleted(claimed.getId()))
                        .onErrorResume(error -> markFailed(claimed.getId(), error))))
                .subscribe(null, error -> log.error("账号注销异步清理调度失败", error));
    }

    /**
     * 认领一条注销任务，保证并发场景下只有一个实例能成功认领。
     *
     * @param candidate   候选任务（从扫描结果获得）
     * @param staleBefore 任务陈旧阈值（防止长时未完成任务永久卡死）
     * @return 认领成功返回原任务 PO；失败（其他实例抢先）返回 empty
     */
    private Mono<AccountDeletionTaskPO> claim(AccountDeletionTaskPO candidate, LocalDateTime staleBefore) {
        if (candidate == null) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> taskMapper.claim(candidate.getId(), LocalDateTime.now(), WORKER_ID, staleBefore))
                .subscribeOn(jdbc())
                .filter(rows -> rows == 1)
                .map(ignored -> candidate);
    }

    /**
     * 将任务标记为成功完成。
     *
     * @param jobId 任务 ID
     * @return 异步完成信号
     */
    private Mono<Void> markCompleted(Long jobId) {
        return Mono.fromRunnable(() -> taskMapper.markCompleted(jobId, LocalDateTime.now()))
                .subscribeOn(jdbc())
                .then();
    }

    /**
     * 将任务标记为失败，并写入最多 500 字符的错误摘要。
     *
     * @param jobId 任务 ID
     * @param error 实际异常（消息截断后落库）
     * @return 异步完成信号
     */
    private Mono<Void> markFailed(Long jobId, Throwable error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        if (message.length() > 500) {
            message = message.substring(0, 500);
        }
        String finalMessage = message;
        return Mono.fromRunnable(() -> taskMapper.markFailed(jobId, finalMessage))
                .subscribeOn(jdbc())
                .then();
    }
}
