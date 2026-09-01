package com.apex.scaffold.interfaces.config;

import com.apex.scaffold.common.util.TraceIdUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.reactivestreams.Subscription;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Reactor MDC 配置。
 *
 * <p>用于在 WebFlux/Reactive 链路中将 Reactor {@link Context} 中的 traceId 同步到 SLF4J MDC，
 * 使日志输出能够携带链路追踪标识。该配置通过 {@link Hooks#onEachOperator(String, java.util.function.Function)}
 * 注册全局 Hook，在每个操作符执行时为当前线程设置/清理 MDC，避免线程复用导致的 MDC 污染。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Configuration
public class ReactorMdcConfig {

    private static final String HOOK_KEY = "mdcContext";

    /**
     * 注册 Reactor 全局 Hook。
     *
     * <p>在每个操作符执行时，将 {@link TraceIdUtil#TRACE_ID_KEY} 对应的 traceId 从 Reactor {@link Context}
     * 写入 MDC，并在执行完成后清理。
     */
    @PostConstruct
    public void setup() {
        Hooks.onEachOperator(HOOK_KEY, Operators.lift((sc, subscriber) -> new MdcContextSubscriber<>(subscriber)));
    }

    /**
     * 释放 Reactor 全局 Hook。
     *
     * <p>在容器销毁时移除本配置注册的 Hook，避免测试或热重载场景下重复注册。
     */
    @PreDestroy
    public void cleanup() {
        Hooks.resetOnEachOperator(HOOK_KEY);
    }

    private static final class MdcContextSubscriber<T> implements CoreSubscriber<T> {

        private final CoreSubscriber<? super T> actual;

        /**
         * 创建 MDC 上下文订阅者。
         *
         * @param actual 实际订阅者
         */
        private MdcContextSubscriber(CoreSubscriber<? super T> actual) {
            this.actual = actual;
        }

        /**
         * 订阅事件回调。
         *
         * <p>包装原始 {@link Subscription}，使请求信号执行时同样从 Reactor {@link Context} 同步
         * MDC，避免 {@code Mono.fromCallable(...).subscribeOn(...)} 在调度线程直接触发任务时丢失 traceId，
         * 并防止线程复用造成的上下文串扰。
         *
         * @param s Subscription
         */
        @Override
        public void onSubscribe(Subscription s) {
            if (s instanceof Fuseable.QueueSubscription) {
                @SuppressWarnings("unchecked")
                Fuseable.QueueSubscription<Object> qs = (Fuseable.QueueSubscription<Object>) s;
                actual.onSubscribe(new MdcContextQueueSubscription<>(qs, this));
            } else {
                actual.onSubscribe(new Subscription() {
                    @Override
                    public void request(long n) {
                        withMdc(currentContext(), () -> s.request(n));
                    }

                    @Override
                    public void cancel() {
                        withMdc(currentContext(), s::cancel);
                    }
                });
            }
        }

        /**
         * 数据到达回调。
         *
         * <p>在转发给下游之前，将 Reactor {@link Context} 中的 traceId 写入 MDC。
         *
         * @param t 数据元素
         */
        @Override
        public void onNext(T t) {
            withMdc(actual.currentContext(), () -> actual.onNext(t));
        }

        /**
         * 异常回调。
         *
         * <p>在转发给下游之前，将 Reactor {@link Context} 中的 traceId 写入 MDC。
         *
         * @param t 异常
         */
        @Override
        public void onError(Throwable t) {
            withMdc(actual.currentContext(), () -> actual.onError(t));
        }

        /**
         * 完成回调。
         *
         * <p>在转发给下游之前，将 Reactor {@link Context} 中的 traceId 写入 MDC。
         */
        @Override
        public void onComplete() {
            withMdc(actual.currentContext(), actual::onComplete);
        }

        /**
         * 获取当前 Reactor 上下文。
         *
         * @return Reactor Context
         */
        @Override
        public Context currentContext() {
            return actual.currentContext();
        }

        /**
         * 在执行 runnable 期间将 traceId 同步到 MDC。
         *
         * <p>该方法会在执行前设置 MDC，在执行结束后清理 MDC，防止线程复用导致 traceId 串扰。
         *
         * @param ctx Reactor Context
         * @param runnable 需要在带 MDC 的上下文中执行的逻辑
         */
        private static void withMdc(Context ctx, Runnable runnable) {
            String traceId = ctx.getOrDefault(TraceIdUtil.TRACE_ID_KEY, null);
            if (traceId != null && !traceId.isEmpty()) {
                MDC.put(TraceIdUtil.TRACE_ID_KEY, traceId);
            } else {
                MDC.remove(TraceIdUtil.TRACE_ID_KEY);
            }

            try {
                runnable.run();
            } finally {
                MDC.remove(TraceIdUtil.TRACE_ID_KEY);
            }
        }
    }

    /**
     * 针对 Reactor Fuseable 优化路径的 QueueSubscription 桥接包装。
     *
     * <p>当上游订阅是 {@link Fuseable.QueueSubscription} 时（常用于可融合的同步队列模式），
     * 必须保持该接口类型，否则下游 Reactor 操作符尝试强转为 {@code QueueSubscription}
     * 会抛出 {@link ClassCastException}。此包装类委托所有 QueueSubscription 方法，并在
     * request/cancel 信号触发时应用 MDC 上下文同步，确保 traceId 在线程切换时不丢失。
     *
     * @param <T> 元素类型
     */
    private static final class MdcContextQueueSubscription<T> implements Fuseable.QueueSubscription<T> {

        private final Fuseable.QueueSubscription<T> delegate;
        private final MdcContextSubscriber<?> subscriber;

        private MdcContextQueueSubscription(
                Fuseable.QueueSubscription<T> delegate, MdcContextSubscriber<?> subscriber) {
            this.delegate = delegate;
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            withMdcStatic(subscriber.currentContext(), () -> delegate.request(n));
        }

        @Override
        public void cancel() {
            withMdcStatic(subscriber.currentContext(), delegate::cancel);
        }

        @Override
        public int requestFusion(int requestedMode) {
            return delegate.requestFusion(requestedMode);
        }

        @Override
        public T poll() {
            return delegate.poll();
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public Iterator<T> iterator() {
            return new Iterator<>() {
                private final Iterator<T> delegateIterator = delegate.iterator();

                @Override
                public boolean hasNext() {
                    return delegateIterator.hasNext();
                }

                @Override
                public T next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return delegateIterator.next();
                }

                @Override
                public void remove() {
                    delegateIterator.remove();
                }
            };
        }
    }

    /**
     * {@link MdcContextSubscriber#withMdc} 方法的静态版本，供外部静态内部类调用。
     *
     * @param ctx      Reactor Context
     * @param runnable 需要在带 MDC 的上下文中执行的逻辑
     */
    private static void withMdcStatic(Context ctx, Runnable runnable) {
        String traceId = ctx.getOrDefault(TraceIdUtil.TRACE_ID_KEY, null);
        if (traceId != null && !traceId.isEmpty()) {
            MDC.put(TraceIdUtil.TRACE_ID_KEY, traceId);
        } else {
            MDC.remove(TraceIdUtil.TRACE_ID_KEY);
        }
        try {
            runnable.run();
        } finally {
            MDC.remove(TraceIdUtil.TRACE_ID_KEY);
        }
    }
}
