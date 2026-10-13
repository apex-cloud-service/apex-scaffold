import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

public class Repro {

    static final class CS<T> implements CoreSubscriber<T> {
        final CoreSubscriber<? super T> actual;
        CS(CoreSubscriber<? super T> actual) { this.actual = actual; }
        @Override
        public void onSubscribe(Subscription s) {
            actual.onSubscribe(new Subscription() {
                @Override public void request(long n) { s.request(n); }
                @Override public void cancel() { s.cancel(); }
            });
        }
        @Override public void onNext(T t) { currentContext(); actual.onNext(t); }
        @Override public void onError(Throwable t) { actual.onError(t); }
        @Override public void onComplete() { actual.onComplete(); }
        @Override public Context currentContext() { return actual.currentContext(); }
    }

    static void register(String key) {
        Hooks.onEachOperator(key, Operators.lift((sc, sub) -> new CS<>(sub)));
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
        for (int i = 0; i < hooks; i++) {
            register("hook" + i);
        }
        try {
            Mono<Integer> m = Mono.just(1);
            for (int i = 0; i < depth; i++) {
                m = step(m, i, useScheduler);
            }
            Integer result = m.contextWrite(c -> c.put("k", "v")).block();
            System.out.println("OK result=" + result);
        } catch (Throwable t) {
            System.out.println("FAILED: " + t.getClass().getName());
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < Math.min(st.length, 30); i++) {
                System.out.println("  " + st[i]);
            }
        }
        Schedulers.shutdownNow();
    }
}
