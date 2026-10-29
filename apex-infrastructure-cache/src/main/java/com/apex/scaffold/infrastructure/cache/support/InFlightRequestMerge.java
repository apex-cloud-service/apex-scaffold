package com.apex.scaffold.infrastructure.cache.support;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/**
 * 回源请求合并器（Single-Flight，缓存击穿防护核心）
 *
 * <p>对同一 key 的并发回源请求做进程内合并：首个请求执行加载逻辑，
 * 其余并发请求共享同一次加载的结果（含成功、空值与错误三种终态），
 * 避免热点 key 过期瞬间的大量并发请求同时穿透到数据库（缓存击穿）。
 *
 * <h2>工作原理</h2>
 * <ol>
 *   <li>首个到达的请求通过 {@code computeIfAbsent} 注册一个带 {@code cache()} 的
 *       共享 Mono 并真正触发回源</li>
 *   <li>后续并发请求命中 {@code inFlight} 表，直接订阅共享 Mono，
 *       与首请求共享同一次回源结果</li>
 *   <li>任一订阅者完成（成功/失败/取消）后从表中移除该条目；
 *       后续新请求走正常缓存读取路径（此时缓存已被首请求回填）</li>
 * </ol>
 *
 * <h2>终态语义</h2>
 * <ul>
 *   <li><b>成功</b>：所有并发订阅者收到同一值</li>
 *   <li><b>空值</b>：DB 无数据时加载器返回空 Mono，并发订阅者均收到空
 *       （配合空值标记防止穿透）</li>
 *   <li><b>错误</b>：加载失败时错误会广播给并发订阅者，条目随即移除，
 *       后续请求可重试</li>
 * </ul>
 *
 * <h2>作用范围</h2>
 * <p>合并范围为本进程内。多实例部署时每个实例最多各自回源一次，
 * 这是与 Guava Cache 一致的标准 Single-Flight 语义；跨实例互斥可由
 * {@code @CacheLock} 分布式锁按业务需要叠加。</p>
 *
 * @author Apex Team
 * @version 1.1.4
 * @since 1.1.4
 */
public final class InFlightRequestMerge {

    /** 进行中的回源请求表：key -> 共享加载 Mono */
    private final ConcurrentHashMap<String, Mono<?>> inFlight = new ConcurrentHashMap<>();

    /**
     * 合并对同一 key 的并发回源请求
     *
     * @param <T>    加载结果类型
     * @param key    合并键（缓存键或业务唯一标识）
     * @param source 回源加载逻辑（仅首个并发请求实际执行）
     * @return 共享的加载结果 Mono
     */
    @SuppressWarnings("unchecked")
    public <T> Mono<T> join(String key, Supplier<Mono<T>> source) {
        if (key == null) {
            return source.get();
        }
        return (Mono<T>) inFlight.computeIfAbsent(key, k -> {
            Mono<T> shared = source.get().cache();
            // 存入表中的正是 doFinally 包装后的实例；终止时以同一实例做精确移除，
            // 防止误删后续注册的新条目（自引用需要 holder 中转）
            Mono<T>[] holder = new Mono[1];
            holder[0] = shared.doFinally(signal -> inFlight.remove(k, holder[0]));
            return holder[0];
        });
    }

    /**
     * 当前进行中的回源请求数量（监控与测试用）
     *
     * @return 进行中的条目数
     */
    public int inflightCount() {
        return inFlight.size();
    }
}
