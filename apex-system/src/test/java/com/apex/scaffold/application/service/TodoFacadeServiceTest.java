package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.application.config.TodoProperties;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.model.entity.SystemMessageEntity;
import com.apex.scaffold.domain.repository.SystemMessageRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

/**
 * 统一待办聚合门面单元测试（S4-a）。
 *
 * <p>通过覆写 HTTP 边界（{@code fetchBody}）注入桩响应体，验证真实聚合、
 * 信封解析、降级映射、舱壁隔离与“个人数据不缓存”语义；不发起真实网络调用。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("统一待办聚合门面测试")
class TodoFacadeServiceTest {

    private static final String OK_WORKFLOW_BODY =
            "{\"code\":0,\"msg\":\"success\",\"data\":{\"records\":[{\"itemType\":\"workflow_task\","
                    + "\"title\":\"待办任务A\",\"occurredAt\":\"2026-11-11T10:00:00\",\"status\":\"PENDING\","
                    + "\"refId\":\"5001\"}],\"total\":1,\"pageNum\":1,\"pageSize\":10,\"hasNext\":false}}";
    private static final String OK_OFFICE_BODY =
            "{\"code\":0,\"msg\":\"success\",\"data\":{\"approvals\":[{\"itemType\":\"office_approval\","
                    + "\"title\":\"审批A\",\"status\":\"PENDING\",\"refId\":\"9001\"}],"
                    + "\"messages\":[{\"itemType\":\"office_message\",\"title\":\"通知信\",\"status\":\"UNREAD\","
                    + "\"refId\":\"7\"}],\"approvalTotal\":2,\"unreadTotal\":1}}";
    private static final String BUSINESS_ERROR_BODY = "{\"code\":500,\"msg\":\"boom\"}";

    /** 空通知分页：通知源无未读消息时的合法负载。 */
    private static final PageResponse<SystemMessageEntity> EMPTY_NOTIFICATION_PAGE =
            PageResponse.of(List.of(), 0L, 1, 10);

    private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    /**
     * 测试桩：覆写 HTTP 边界与进程内通知仓储，其余聚合/解析/降级逻辑走真实实现。
     */
    private final class StubFacadeService extends TodoFacadeService {

        private final Map<String, Mono<String>> stubBodies = new HashMap<>();

        private final AtomicInteger fetchCount = new AtomicInteger();

        private final AtomicReference<String> lastAuthorization = new AtomicReference<>();

        StubFacadeService(TodoProperties properties, SystemMessageRepository repository) {
            super(
                    properties,
                    mapper,
                    WebClient.builder(),
                    new com.apex.scaffold.common.internal.InternalAuthProperties(),
                    null,
                    repository);
        }

        @Override
        protected Mono<String> fetchBody(String url, String authorization) {
            fetchCount.incrementAndGet();
            lastAuthorization.set(authorization);
            Mono<String> body = stubBodies.get(url.contains("/workflow/") ? "workflow" : "office");
            return body != null ? body : Mono.error(new IllegalStateException("unreachable"));
        }
    }

    /**
     * 通知仓储桩：按用户返回两页内固定未读消息，可注入失败。
     */
    private static final class StubNotificationRepository implements SystemMessageRepository {

        private final Mono<PageResponse<SystemMessageEntity>> pageByUserResult;

        private StubNotificationRepository(Mono<PageResponse<SystemMessageEntity>> pageByUserResult) {
            this.pageByUserResult = pageByUserResult;
        }

        @Override
        public Mono<SystemMessageEntity> save(SystemMessageEntity entity) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<List<SystemMessageEntity>> listByUserId(Long userId, int offset, int limit) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<Long> countByUserId(Long userId) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<PageResponse<SystemMessageEntity>> pageByUser(Long userId, int page, int size, boolean unreadOnly) {
            return pageByUserResult;
        }

        @Override
        public Mono<Long> countUnread(Long userId) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<Boolean> markRead(Long userId, Long messageId) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<Long> markAllRead(Long userId) {
            return Mono.error(new UnsupportedOperationException());
        }
    }

    private static SystemMessageEntity notification(long id, String content) {
        SystemMessageEntity entity = new SystemMessageEntity();
        entity.setId(id);
        entity.setContent(content);
        entity.setCreateTime(LocalDateTime.of(2026, 11, 11, 9, 0));
        return entity;
    }

    @Test
    @DisplayName("三源全部可用：聚合结果 degraded=false，Authorization 透传，来源标识回填")
    void allSourcesUp() {
        StubFacadeService service = new StubFacadeService(
                new TodoProperties(),
                new StubNotificationRepository(Mono.just(
                        PageResponse.of(List.of(notification(1L, "系统通知一"), notification(2L, "系统通知二")), 2L, 1, 10))));
        service.stubBodies.put("workflow", Mono.just(OK_WORKFLOW_BODY));
        service.stubBodies.put("office", Mono.just(OK_OFFICE_BODY));

        StepVerifier.create(service.getUnifiedTodo(2001L, "Bearer token"))
                .assertNext(response -> {
                    assertThat(response.userId()).isEqualTo(2001L);
                    assertThat(response.sources().get("workflow").status())
                            .as(
                                    "workflow: %s",
                                    response.sources().get("workflow").error())
                            .isEqualTo("UP");
                    assertThat(response.sources().get("office").status())
                            .as("office: %s", response.sources().get("office").error())
                            .isEqualTo("UP");
                    assertThat(response.sources().get("notifications").status())
                            .as(
                                    "notifications: %s",
                                    response.sources().get("notifications").error())
                            .isEqualTo("UP");
                    assertThat(response.degraded()).isFalse();
                    assertThat(response.sources().get("workflow").status()).isEqualTo("UP");
                    assertThat(response.sources().get("workflow").total()).isEqualTo(1L);
                    assertThat(response.sources().get("workflow").items()).hasSize(1);
                    assertThat(response.sources().get("workflow").items().get(0).source())
                            .isEqualTo("workflow");
                    // office 两组条目合并，总数相加
                    assertThat(response.sources().get("office").status()).isEqualTo("UP");
                    assertThat(response.sources().get("office").total()).isEqualTo(3L);
                    assertThat(response.sources().get("office").items())
                            .extracting(item -> item.type())
                            .containsExactly("office_approval", "office_message");
                    // 通知源为进程内调用
                    assertThat(response.sources().get("notifications").status()).isEqualTo("UP");
                    assertThat(response.sources().get("notifications").total()).isEqualTo(2L);
                    assertThat(response.sources()
                                    .get("notifications")
                                    .items()
                                    .get(0)
                                    .title())
                            .isEqualTo("系统通知一");
                })
                .verifyComplete();

        assertThat(service.lastAuthorization.get()).isEqualTo("Bearer token");
    }

    @Test
    @DisplayName("个人数据不缓存不合并：两次调用均重新拉取下游（2 源 × 2 次）")
    void noCacheNoInFlightMerge() {
        StubFacadeService service = new StubFacadeService(
                new TodoProperties(), new StubNotificationRepository(Mono.just(EMPTY_NOTIFICATION_PAGE)));
        service.stubBodies.put("workflow", Mono.just(OK_WORKFLOW_BODY));
        service.stubBodies.put("office", Mono.just(OK_OFFICE_BODY));

        StepVerifier.create(service.getUnifiedTodo(2001L, "Bearer token"))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(service.getUnifiedTodo(2001L, "Bearer token"))
                .expectNextCount(1)
                .verifyComplete();
        assertThat(service.fetchCount.get()).isEqualTo(4);

        // 同用户同凭证的并发请求同样不合并
        service.fetchCount.set(0);
        StepVerifier.create(Mono.zip(
                        service.getUnifiedTodo(2001L, "Bearer token"), service.getUnifiedTodo(2001L, "Bearer token")))
                .assertNext(tuple -> {
                    assertThat(tuple.getT1()).isNotSameAs(tuple.getT2());
                    assertThat(tuple.getT1().degraded()).isFalse();
                })
                .verifyComplete();
        assertThat(service.fetchCount.get()).isEqualTo(4);
    }

    @Test
    @DisplayName("单源超时降级：部分可用，整体 degraded=true，该源结果缺失并带错误摘要")
    void workflowDegradedOnTimeout() {
        StubFacadeService service = new StubFacadeService(
                new TodoProperties(), new StubNotificationRepository(Mono.just(EMPTY_NOTIFICATION_PAGE)));
        service.stubBodies.put("workflow", Mono.error(new TimeoutException("no response within timeout")));
        service.stubBodies.put("office", Mono.just(OK_OFFICE_BODY));

        StepVerifier.create(service.getUnifiedTodo(2001L, null))
                .assertNext(response -> {
                    assertThat(response.degraded()).isTrue();
                    assertThat(response.sources().get("workflow").status()).isEqualTo("DEGRADED");
                    assertThat(response.sources().get("workflow").error()).isEqualTo("TimeoutException");
                    assertThat(response.sources().get("workflow").items()).isNull();
                    assertThat(response.sources().get("office").status()).isEqualTo("UP");
                    assertThat(response.sources().get("notifications").status()).isEqualTo("UP");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("下游业务错误与非法响应体均按源降级")
    void businessErrorAndInvalidBodyDegradePerSource() {
        StubFacadeService service = new StubFacadeService(
                new TodoProperties(), new StubNotificationRepository(Mono.just(EMPTY_NOTIFICATION_PAGE)));
        service.stubBodies.put("workflow", Mono.just(BUSINESS_ERROR_BODY));
        service.stubBodies.put("office", Mono.just("not-json"));

        StepVerifier.create(service.getUnifiedTodo(2001L, "Bearer token"))
                .assertNext(response -> {
                    assertThat(response.degraded()).isTrue();
                    assertThat(response.sources().get("workflow").status()).isEqualTo("DEGRADED");
                    assertThat(response.sources().get("workflow").error()).isEqualTo("invalid response body");
                    assertThat(response.sources().get("office").status()).isEqualTo("DEGRADED");
                    assertThat(response.sources().get("office").error()).isEqualTo("invalid response body");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("通知源失败按源降级，不影响其他源")
    void notificationFailureDegradesOnlyNotifications() {
        StubFacadeService service = new StubFacadeService(
                new TodoProperties(), new StubNotificationRepository(Mono.error(new IllegalStateException("db down"))));
        service.stubBodies.put("workflow", Mono.just(OK_WORKFLOW_BODY));
        service.stubBodies.put("office", Mono.just(OK_OFFICE_BODY));

        StepVerifier.create(service.getUnifiedTodo(2001L, "Bearer token"))
                .assertNext(response -> {
                    assertThat(response.degraded()).isTrue();
                    assertThat(response.sources().get("notifications").status()).isEqualTo("DEGRADED");
                    assertThat(response.sources().get("notifications").error()).isEqualTo("IllegalStateException");
                    assertThat(response.sources().get("workflow").status()).isEqualTo("UP");
                    assertThat(response.sources().get("office").status()).isEqualTo("UP");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("未配置地址的数据源按 DEGRADED 降级，不影响其他源")
    void unconfiguredSourceDegrades() {
        TodoProperties properties = new TodoProperties();
        properties.getSources().remove("office");
        StubFacadeService service =
                new StubFacadeService(properties, new StubNotificationRepository(Mono.just(EMPTY_NOTIFICATION_PAGE)));
        service.stubBodies.put("workflow", Mono.just(OK_WORKFLOW_BODY));

        StepVerifier.create(service.getUnifiedTodo(2001L, "Bearer token"))
                .assertNext(response -> {
                    assertThat(response.degraded()).isTrue();
                    assertThat(response.sources().get("office").status()).isEqualTo("DEGRADED");
                    assertThat(response.sources().get("office").error()).isEqualTo("source not configured");
                    assertThat(response.sources().get("workflow").status()).isEqualTo("UP");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("舱壁并发隔离：饱和源立即降级且不影响其他源，许可随上游终止释放")
    void bulkheadSaturationDegradesOnlyThatSourceAndReleasesOnTerminate() {
        TodoProperties properties = new TodoProperties();
        properties.getBulkhead().put("office", 1);
        StubFacadeService service =
                new StubFacadeService(properties, new StubNotificationRepository(Mono.just(EMPTY_NOTIFICATION_PAGE)));
        service.stubBodies.put("workflow", Mono.just(OK_WORKFLOW_BODY));
        // office 源挂起不完成：第一路请求持有唯一许可
        Sinks.One<String> officeSink = Sinks.one();
        service.stubBodies.put("office", officeSink.asMono());

        reactor.core.Disposable first =
                service.getUnifiedTodo(2001L, "Bearer a").subscribe();
        try {
            // 第二路请求：office 源饱和降级，其他源不受影响
            StepVerifier.create(service.getUnifiedTodo(2001L, "Bearer b"))
                    .assertNext(response -> {
                        assertThat(response.sources().get("office").status()).isEqualTo("DEGRADED");
                        assertThat(response.sources().get("office").error()).isEqualTo("bulkhead saturated");
                        assertThat(response.sources().get("workflow").status()).isEqualTo("UP");
                        assertThat(response.sources().get("notifications").status())
                                .isEqualTo("UP");
                    })
                    .verifyComplete();
        } finally {
            // 让第一路的上游刷新正常终止：许可随之释放
            officeSink.tryEmitValue(OK_OFFICE_BODY);
            first.dispose();
        }

        // 许可已释放：office 源恢复可拉取，全源 UP
        StepVerifier.create(service.getUnifiedTodo(2001L, "Bearer c"))
                .assertNext(response -> {
                    assertThat(response.degraded()).isFalse();
                    assertThat(response.sources().get("office").status()).isEqualTo("UP");
                })
                .verifyComplete();
    }
}
