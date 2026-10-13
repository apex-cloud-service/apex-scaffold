package com.apex.scaffold.interfaces.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.util.TraceIdUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class ReactorMdcTenantIsolationTest {

    private static final String REACTOR_MDC_HOOK_KEY = "mdcContext";

    private ReactorMdcConfig mdcConfig;
    private Scheduler scheduler;
    private boolean tenantHooksReset;

    @BeforeEach
    void setUp() {
        Hooks.resetOnEachOperator(REACTOR_MDC_HOOK_KEY);
        TenantContext.resetGlobalHooks();
        TenantContext.initializeGlobalHooks();
        tenantHooksReset = true;
        mdcConfig = new ReactorMdcConfig();
        mdcConfig.setup();
        scheduler = Schedulers.newSingle("mdc-tenant-isolation");
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.dispose();
        }
        if (mdcConfig != null) {
            mdcConfig.cleanup();
        }
        if (tenantHooksReset) {
            TenantContext.resetGlobalHooks();
            TenantContext.initializeGlobalHooks();
        }
        TenantContext.clear();
        MDC.clear();
    }

    @Test
    void scheduledTaskShouldCarryTraceWithMdcHook() {
        Mono<String> capture = Mono.fromCallable(() -> {
                    String traceId = MDC.get(TraceIdUtil.TRACE_ID_KEY);
                    return traceId == null ? "missing" : traceId;
                })
                .subscribeOn(scheduler);

        StepVerifier.create(capture.contextWrite(ctx -> ctx.put(TraceIdUtil.TRACE_ID_KEY, "trace-a")))
                .expectNext("trace-a")
                .verifyComplete();
    }

    @Test
    void callbacksShouldRestoreExistingTrace() {
        MDC.put(TraceIdUtil.TRACE_ID_KEY, "outer-trace");
        MDC.put("tid", "agent-trace");
        Mono<String> capture = Mono.fromCallable(() -> MDC.get(TraceIdUtil.TRACE_ID_KEY))
                .contextWrite(ctx -> ctx.put(TraceIdUtil.TRACE_ID_KEY, "request-trace"));

        StepVerifier.create(capture).expectNext("request-trace").verifyComplete();

        assertThat(MDC.get(TraceIdUtil.TRACE_ID_KEY)).isEqualTo("outer-trace");
        assertThat(MDC.get("tid")).isEqualTo("agent-trace");
    }

    @Test
    void callbacksWithoutContextShouldRestoreExistingTrace() {
        MDC.put(TraceIdUtil.TRACE_ID_KEY, "outer-trace");
        Mono<String> capture = Mono.fromCallable(() -> {
                    assertThat(MDC.get(TraceIdUtil.TRACE_ID_KEY)).isNull();
                    return "no-request-trace";
                })
                .hide();

        StepVerifier.create(capture).expectNext("no-request-trace").verifyComplete();

        assertThat(MDC.get(TraceIdUtil.TRACE_ID_KEY)).isEqualTo("outer-trace");
    }

    @Test
    void errorCallbacksShouldRestoreExistingTrace() {
        MDC.put(TraceIdUtil.TRACE_ID_KEY, "outer-trace");
        Mono<Void> failure = Mono.<Void>error(new IllegalStateException("request failure"))
                .contextWrite(ctx -> ctx.put(TraceIdUtil.TRACE_ID_KEY, "request-trace"));

        StepVerifier.create(failure).expectError(IllegalStateException.class).verify();

        assertThat(MDC.get(TraceIdUtil.TRACE_ID_KEY)).isEqualTo("outer-trace");
    }

    @Test
    void scheduledTaskShouldCarryTenantViaWithTenantBridge() {
        Mono<String> capture = TenantContext.withTenant(
                        Mono.fromCallable(() -> TenantContext.currentTenantId().orElse("missing")))
                .subscribeOn(scheduler);

        StepVerifier.create(capture.contextWrite(TenantContext.withTenant("tenant-a")))
                .expectNext("tenant-a")
                .verifyComplete();
    }

    @Test
    void scheduledTaskShouldCarryTenantFromThreadLocalAtScheduleTime() {
        TenantContext.setTenantId("tenant-a");
        try {
            Mono<String> capture = Mono.fromCallable(
                            () -> TenantContext.currentTenantId().orElse("missing"))
                    .subscribeOn(scheduler);

            StepVerifier.create(capture).expectNext("tenant-a").verifyComplete();
        } finally {
            TenantContext.clear();
        }

        assertThat(TenantContext.currentTenantId()).isEmpty();
    }

    @Test
    void scheduledTaskShouldCarryTenantAndTraceWithoutLeakingMdc() {
        Mono<String> capture = TenantContext.withTenant(Mono.fromCallable(() -> {
                    String tenantId = TenantContext.currentTenantId().orElse("missing");
                    String traceId = MDC.get(TraceIdUtil.TRACE_ID_KEY);
                    return tenantId + "|" + (traceId == null ? "missing" : traceId);
                }))
                .subscribeOn(scheduler);

        StepVerifier.create(capture.contextWrite(TenantContext.withTenant("tenant-a"))
                        .contextWrite(ctx -> ctx.put(TraceIdUtil.TRACE_ID_KEY, "trace-a")))
                .expectNext("tenant-a|trace-a")
                .verifyComplete();

        StepVerifier.create(TenantContext.withTenant(Mono.fromCallable(() -> {
                            String tenantId = TenantContext.currentTenantId().orElse("missing");
                            String traceId = MDC.get(TraceIdUtil.TRACE_ID_KEY);
                            return tenantId + "|" + (traceId == null ? "missing" : traceId);
                        }))
                        .subscribeOn(scheduler))
                .expectNext("missing|missing")
                .verifyComplete();

        assertThat(TenantContext.currentTenantId()).isEmpty();
        assertThat(MDC.get(TraceIdUtil.TRACE_ID_KEY)).isNull();
    }
}
