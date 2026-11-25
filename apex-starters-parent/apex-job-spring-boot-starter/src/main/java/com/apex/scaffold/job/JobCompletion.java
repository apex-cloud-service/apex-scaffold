package com.apex.scaffold.job;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import java.time.Duration;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

/**
 * XXL 同步回调边界：等待完整业务流，超时及业务异常交由 XXL 标记失败。
 *
 * @author Apex Scaffold
 * @since 1.3.1
 */
@lombok.NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class JobCompletion {

    /**
     * 在专用阻塞池订阅并等待任务全部完成。
     *
     * <p>等价于「订阅 → 等待完成信号」：必须等全部元素与终止信号到达才返回，禁止提前报告成功；
     * 超过 5 分钟未完成时抛出超时异常。
     *
     * @param task 待等待的发布者，通常为任务执行返回的 {@code Mono}/{@code Flux}
     */
    public static void await(Publisher<?> task) {
        Flux.from(task).then().subscribeOn(BlockingIoSchedulers.general()).block(Duration.ofMinutes(5));
    }
}
