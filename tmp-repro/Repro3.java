import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;
import reactor.core.publisher.Hooks;
import reactor.util.context.Context;

public class Repro3 {

    static final String KEY = "tenant";

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

        @Override public Context currentContext() { return actual.currentContext(); }

        void with(Runnable r) {
            String v = actual.currentContext().getOrDefault(KEY, null);
            if (v == null) { r.run(); return; }
            r.run();
        }
    }

    static final class WSub2 implements Subscription {
        final Subscription d; final WSub<?> s;
        WSub2(Subscription d, WSub<?> s) { this.d = d; this.s = s; }
        @Override public void request(long n) { s.with(() -> d.request(n)); }
        @Override public void cancel() { s.with(d::cancel); }
    }

    static final class WQueueSub<T> implements Fuseable.QueueSubscription<T> {
        final Fuseable.QueueSubscription<T> d; final WSub<?> s;
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

    // mimic TenantContext.withTenant(Mono)
    static <T> Mono<T> withTenant(Mono<T> publisher) {
        return Mono.deferContextual(context -> {
            String tenantId = context.getOrDefault(KEY, null);
            if (tenantId == null) { return publisher; }
            return Mono.fromDirect((Publisher<T>) subscriber -> publisher.subscribe(subscriber));
        });
    }

    static Context withTenantCtx(String tenantId) {
        return Context.of(KEY, tenantId);
    }

    public static void main(String[] args) {
        int hooks = args.length > 0 ? Integer.parseInt(args[0]) : 3;
        int depth = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        for (int i = 0; i < hooks; i++) { register("hook" + i, "W" + i); }
        try {
            Mono<Integer> mono = Mono.just(1)
                    .flatMap(x -> Mono.just(x + 1))
                    .map(x -> x * 2)
                    .flatMap(x -> Mono.just(x));
            for (int i = 0; i < depth; i++) {
                Mono<Integer> prev = mono;
                mono = withTenant(prev).contextWrite(withTenantCtx("t")) ;
            }
            Integer result = mono.block();
            System.out.println("OK result=" + result);
        } catch (Throwable t) {
            System.out.println("FAILED: " + t.getClass().getName());
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < Math.min(st.length, 40); i++) {
                System.out.println("  " + st[i]);
            }
        }
    }
}
