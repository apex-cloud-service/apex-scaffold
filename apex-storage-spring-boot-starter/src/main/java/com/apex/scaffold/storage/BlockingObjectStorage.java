package com.apex.scaffold.storage;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import java.io.InputStream;
import java.util.concurrent.Callable;
import reactor.core.publisher.Mono;

/**
 * 将同步 SDK 边界延迟到订阅，并统一隔离到专用存储线程池。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class BlockingObjectStorage implements ObjectStorage {
    private static final int MAX_TTL_SECONDS = 604800;
    /** @param action 同步操作 @param <T> 结果类型 @return 延迟执行结果 */
    protected static <T> Mono<T> io(Callable<T> action) {
        return Mono.fromCallable(action).subscribeOn(BlockingIoSchedulers.storage());
    }

    /** @param stream 待关闭资源 @return 专用存储池上的关闭信号 */
    static Mono<Void> closeStream(InputStream stream) {
        return io(() -> {
                    stream.close();
                    return true;
                })
                .then();
    }

    /** @param ttlSeconds 有效秒数 @throws IllegalArgumentException 超出所有后端共同支持范围 */
    protected static void validateTtl(int ttlSeconds) {
        if (ttlSeconds < 1 || ttlSeconds > MAX_TTL_SECONDS) {
            throw new IllegalArgumentException("签名有效期必须在 1 至 604800 秒之间");
        }
    }
}
