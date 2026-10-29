package com.apex.scaffold.seata.saga;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.seata.saga.SagaJdbcAction.Invocation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Mono;

/** DB2 只有本地数据源与事务；环回 HTTP 不初始化第二套 Seata 客户端。 */
public final class SagaDb2HttpFixture implements AutoCloseable {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private final HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final AtomicReference<RuntimeException> actionFailure = new AtomicReference<>();
    private final List<SagaJdbcAction.ForcedLocalFailure> localFailures = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public SagaDb2HttpFixture(SagaJdbcAction action) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/debit", exchange -> handle(exchange, action, false));
        server.createContext("/restore", exchange -> handle(exchange, action, true));
        server.start();
    }

    public Boolean debit(String businessKey, Integer amount, Boolean failAfterWrites) {
        return invoke("/debit", new Request(businessKey, amount, failAfterWrites, Invocation.capture()));
    }

    public Boolean restore(String businessKey, Integer amount) {
        return invoke("/restore", new Request(businessKey, amount, false, Invocation.capture()));
    }

    private Boolean invoke(String path, Request body) {
        // 原始 RM 栈在切换线程/HTTP 前采集；传递证据，不替门面绑定或伪造外层 XID。
        return Mono.fromCallable(() -> {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(
                                    "http://127.0.0.1:" + server.getAddress().getPort() + path))
                            .timeout(TIMEOUT)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)))
                            .build();
                    int code = client.send(request, HttpResponse.BodyHandlers.discarding())
                            .statusCode();
                    if (code != 204) {
                        RuntimeException failure = actionFailure.getAndSet(null);
                        if (failure != null) {
                            throw failure;
                        }
                        throw new IllegalStateException("DB2 本地动作 HTTP 响应失败: " + code);
                    }
                    return true;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .block(TIMEOUT);
    }

    private void handle(HttpExchange exchange, SagaJdbcAction action, boolean compensate) throws IOException {
        try {
            Request request = mapper.readValue(exchange.getRequestBody(), Request.class);
            if (compensate) {
                action.restore(request.businessKey(), request.amount(), request.invocation());
            } else {
                action.debit(request.businessKey(), request.amount(), request.failAfterWrites(), request.invocation());
            }
            exchange.sendResponseHeaders(204, -1);
        } catch (RuntimeException failure) {
            actionFailure.set(failure);
            if (failure instanceof SagaJdbcAction.ForcedLocalFailure localFailure) {
                localFailures.add(localFailure);
            }
            exchange.sendResponseHeaders(500, -1);
        } finally {
            exchange.close();
        }
    }

    public List<SagaJdbcAction.ForcedLocalFailure> localFailures() {
        return List.copyOf(localFailures);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            server.stop(0);
        }
    }

    private record Request(String businessKey, Integer amount, Boolean failAfterWrites, Invocation invocation) {}
}
