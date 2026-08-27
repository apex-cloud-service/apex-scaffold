package com.apex.scaffold.application.job;

import com.apex.scaffold.domain.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 定时任务调度恢复初始化器。
 *
 * <p>应用启动完成后，从数据库加载全部启用状态的任务并注册到
 * {@link JobScheduler}，使任务调度在应用重启后自动恢复。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobInitializer implements ApplicationRunner {

    /**
     * 定时任务仓储。
     */
    private final JobRepository jobRepository;

    /**
     * 定时任务调度器。
     */
    private final JobScheduler jobScheduler;

    /**
     * 应用启动后恢复全部启用任务的调度。
     *
     * @param args 应用启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        jobRepository
                .findEnabled()
                .flatMapMany(Flux::fromIterable)
                .concatMap(jobScheduler::register)
                .then()
                .subscribe(ignored -> log.info("定时任务调度恢复完成"), error -> log.error("定时任务调度恢复失败", error));
    }
}
