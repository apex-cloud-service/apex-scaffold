package com.apex.scaffold.contract.support;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import java.util.Objects;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * 同步 Feign 调用的响应式桥接工具.
 *
 * <p><b>背景</b>：内部 API 契约中的 Feign 客户端为「声明式同步」接口（{@code Result<T>}
 * 返回值）。在 WebFlux 响应式主链路中若直接阻塞调用，会占用事件循环线程并触发 Reactor 的
 * 阻塞检测（BlockHound / {@code ReactorDebugAgent}）。本工具将同步调用统一调度到
 * {@link BlockingIoSchedulers#remote()} 隔离线程池上执行，再以 {@link Mono} 返回，
 * 使远程调用与响应式主链路安全衔接。
 *
 * <p><b>桥接范式</b>（迁移方案 §6.3，为跨服务调用的唯一标准写法）：
 * <pre>{@code
 * Mono<Result<UserSnapshotDTO>> mono =
 *         FeignReactiveBridge.bridge(() -> systemFeignClient.getUserSnapshot(accountId));
 * }</pre>
 *
 * <p><b>为什么必须用 {@code BlockingIoSchedulers.remote()}</b>：该池为同步远程 SDK / HTTP
 * 专用隔离池（{@code apex.remote}，16 线程 / 1024 队列），避免阻塞调用挤占 JDBC、CACHE
 * 等核心线程池；同时禁止使用 Reactor 全局 {@code Schedulers.boundedElastic()}。
 *
 * <p><b>注意</b>：仅「同步阻塞」的 Feign 调用需要本桥接；若使用 {@code WebClient} /
 * {@code ManagedWebClientFactory} 的异步调用，则无需任何 Scheduler，也不应经过本工具。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public final class FeignReactiveBridge {

    /**
     * 同步远程调用专用 Scheduler（{@code apex.remote}）.
     *
     * <p>以静态字段持有 {@link BlockingIoSchedulers#remote()} 返回值。该 Scheduler 由
     * {@code BlockingIoSchedulers} 在进程生命周期内保证可用（被 dispose 时自动重建），
     * 因此此处引用是安全的。</p>
     */
    private static final Scheduler REMOTE_SCHEDULER = BlockingIoSchedulers.remote();

    private FeignReactiveBridge() {
        throw new UnsupportedOperationException("工具类禁止实例化");
    }

    /**
     * 将同步 Feign 调用桥接为响应式 {@link Mono}.
     *
     * <p>订阅发生时在 {@code apex.remote} 线程池执行 {@code supplier}，调用方可在主链路上
     * 继续进行非阻塞编排。</p>
     *
     * @param supplier 同步 Feign 调用（例如 {@code () -> systemFeignClient.getUserSnapshot(id)}）
     * @param <T>      业务返回值类型（通常为 {@link Result} 包装）
     * @return 在远程专用线程池上执行的 {@link Mono}
     * @throws NullPointerException 当 {@code supplier} 为 {@code null} 时
     */
    public static <T> Mono<T> bridge(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier 不能为 null");
        return Mono.fromCallable(supplier::get).subscribeOn(REMOTE_SCHEDULER);
    }

    /**
     * 将同步返回 {@link Result} 的 Feign 调用桥接为响应式 {@link Mono}.
     *
     * <p>语义与 {@link #bridge(Supplier)} 一致，仅作为可读性重载明确调用契约：入参返回值为
     * 统一的 {@link Result} 响应体，便于调用方直接 {@code flatMap} 判定 {@code success}。</p>
     *
     * @param supplier 同步 Feign 调用（返回 {@link Result}）
     * @param <T>      Result 承载的业务数据类型
     * @return 在远程专用线程池上执行的 {@link Mono}
     */
    public static <T> Mono<Result<T>> bridgeResult(Supplier<Result<T>> supplier) {
        return bridge(supplier);
    }
}
