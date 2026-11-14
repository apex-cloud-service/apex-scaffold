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

    /** 在专用阻塞池订阅，等待全部元素与完成信号，禁止提前报告成功。 */
    public static void await(Publisher<?> task) {
        Flux.from(task).then().subscribeOn(BlockingIoSchedulers.general()).block(Duration.ofMinutes(5));
    }
}
