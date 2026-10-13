import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

public class Repro2 {

    static final String KEY = "k";

    static final class WSub<T> implements CoreSubscriber<T> {
        final String tag;
        final CoreSubscriber<? super T> actual;
        WSub(String tag, CoreSubscriber<? super T> actual) { this.tag = tag; this.actual = actual; }

        @Override
        public void onSubscribe(Subscription s) {
            if (s instanceof Fuseable.QueueSubscription<?> qs) {
                actual.onSubscribe(new WQueueSub<>((Fuseable.QueueSubscription<T>) qs, this));
            } else {
                actual.onSubscribe(new WSub2(s, this));
            }
        }

        @Override public void onNext(T t) { with(() -> actual.onNext(t)); }
        @Override public void onError(Throwable t) { with(() -> actual.onError(t)); }
        @Override public void onComplete() { with(actual::onComplete); }

        @Override
        public Context currentContext() {
            return actual.currentContext();
        }

        void with(Runnable r) {
            Object v = actual.currentContext().getOrDefault(KEY, null);
            if (v == null) { r.run(); return; }
            r.run();
        }
    }

    static final class WSub2 implements Subscription {
        final Subscription d;
        final WSub<?> s;
        WSub2(Subscription d, WSub<?> s) { this.d = d; this.s = s; }
        @Override public void request(long n) { s.with(() -> d.request(n)); }
        @Override public void cancel() { s.with(d::cancel); }
    }

    static final class WQueueSub<T> implements Fuseable.QueueSubscription<T> {
        final Fuseable.QueueSubscription<T> d;
        final WSub<?> s;
        WQueueSub(Fuseable.QueueSubscription<T> d, WSub<?> s) { this.d = d; this.s = s; }
        @Override public void request(long n) { s.with(() -> d.request(n)); }
        @Override public void cancel() { s.with(d::cancel); }
        @Override public T poll() { return d.poll(); }
        @Override public int size() { return d.size(); }
        @Override public boolean isEmpty() { return d.isEmpty(); }
        @Override public void clear() { d.clear(); }
        @Override public int requestFusion(int mode) { return d.requestFusion(mode); }
    }

    static void register(String key, String tag) {
        Hooks.onEachOperator(key, Operators.lift((sc, sub) -> new WSub<>(tag, sub)));
    }

    static Mono<Integer> step(Mono<Integer> prev, int k, boolean sched) {
        Mono<Integer> m = Mono.defer(() -> prev)
                .map(x -> x + k)
                .doOnEach(sig -> {})
                .flatMap(x -> Mono.just(x).subscribeOn(Schedulers.parallel()));
        if (sched) {
            m = m.subscribeOn(Schedulers.parallel());
        }
        return m;
    }

    public static void main(String[] args) {
        int hooks = args.length > 0 ? Integer.parseInt(args[0]) : 3;
        int depth = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        boolean useScheduler = args.length > 2 && Boolean.parseBoolean(args[2]);
        boolean useFlux = args.length > 3 && Boolean.parseBoolean(args[3]);
        for (int i = 0; i < hooks; i++) {
            register("hook" + i, "W" + i);
        }
        try {
            if (useFlux) {
                Flux<Integer> f = Flux.just(1, 2);
                for (int i = 0; i < depth; i++) {
                    final int k = i;
                    Flux<Integer> prev = f;
                    f = prev.concatMap(x -> Mono.just(x + k)).map(x -> x * 2);
                }
                Long result = f.contextWrite(c -> c.put(KEY, "v")).count().block();
                System.out.println("OK flux count=" + result);
            } else {
                Mono<Integer> m = Mono.just(1);
                for (int i = 0; i < depth; i++) {
                    m = step(m, i, useScheduler);
                }
                Integer result = m.contextWrite(c -> c.put(KEY, "v")).block();
                System.out.println("OK result=" + result);
            }
        } catch (Throwable t) {
            System.out.println("FAILED: " + t.getClass().getName());
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < Math.min(st.length, 40); i++) {
                System.out.println("  " + st[i]);
            }
        }
        Schedulers.shutdownNow();
    }
}
