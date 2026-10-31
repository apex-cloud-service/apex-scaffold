package com.apex.scaffold.officeservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.gateway.filter.JwtSignatureFilter;
import com.apex.scaffold.gateway.filter.UserContextRelayFilter;
import com.apex.scaffold.gateway.security.GatewayJwtVerifierService;
import com.apex.scaffold.gateway.security.JwksProperties;
import com.apex.scaffold.office.off.application.assembler.OffMessageAssembler;
import com.apex.scaffold.office.off.application.service.OffMessageService;
import com.apex.scaffold.office.off.infrastructure.persistence.converter.OffMessageDeliveryPoConverter;
import com.apex.scaffold.office.off.infrastructure.persistence.converter.OffMessagePoConverter;
import com.apex.scaffold.office.off.infrastructure.persistence.converter.OffMessageRecipientPoConverter;
import com.apex.scaffold.office.off.infrastructure.persistence.mapper.OffEmployeeMapper;
import com.apex.scaffold.office.off.infrastructure.persistence.mapper.OffMessageDeliveryMapper;
import com.apex.scaffold.office.off.infrastructure.persistence.mapper.OffMessageMapper;
import com.apex.scaffold.office.off.infrastructure.persistence.mapper.OffMessageRecipientMapper;
import com.apex.scaffold.office.off.infrastructure.persistence.repository.MessageDeliveryRepositoryImpl;
import com.apex.scaffold.office.off.infrastructure.persistence.repository.OffMessageDeliveryRepositoryImpl;
import com.apex.scaffold.office.off.infrastructure.persistence.repository.OffMessageRecipientRepositoryImpl;
import com.apex.scaffold.office.off.infrastructure.persistence.repository.OffMessageRepositoryImpl;
import com.apex.scaffold.officeservice.config.MessageWebSocketConfig;
import com.apex.scaffold.officeservice.interfaces.off.handler.MessageHandler;
import com.apex.scaffold.officeservice.interfaces.off.router.MessageRouter;
import com.apex.scaffold.officeservice.interfaces.util.RequestHandlerUtil;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/**
 * 以真实网关 HTTP/WS 转发和办公 H2 事务验证单实例消息联合链路。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
class MessageGatewayDatabaseIntegrationTest {
    private static final String SECRET = "office-test-key-0123456789-abcdef0123456789";
    private static final String ORIGIN = "https://app.example";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final String BASE = "/api/v1/messages";

    /** 经网关签发票据、拒绝非法来源并升级，收到提示时数据库已提交且可经网关读取。 */
    @Test
    void forwardsHttpAndWebSocketToSameOfficeWithSanitizedIdentity() throws Exception {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/basic-schema.sql")).execute(source);
        var jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO off_employee(employee_code,employee_name,dept_id,user_id,tenant_id)"
                + " VALUES ('e7','接收人',1,7,'a')");
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        var interceptor = new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(
                new com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler() {
                    @Override
                    public net.sf.jsqlparser.expression.Expression getTenantId() {
                        return new net.sf.jsqlparser.expression.StringValue(TenantContext.currentTenantId()
                                .orElseThrow(() -> new IllegalStateException("test tenant missing")));
                    }

                    @Override
                    public boolean ignoreTable(String tableName) {
                        return false;
                    }
                }));
        factory.setPlugins(interceptor);
        var sqlFactory = factory.getObject();
        sqlFactory.getConfiguration().addMapper(OffMessageMapper.class);
        sqlFactory.getConfiguration().addMapper(OffMessageRecipientMapper.class);
        sqlFactory.getConfiguration().addMapper(OffMessageDeliveryMapper.class);
        sqlFactory.getConfiguration().addMapper(OffEmployeeMapper.class);
        var sql = new SqlSessionTemplate(sqlFactory);
        var messages = sql.getMapper(OffMessageMapper.class);
        var recipients = sql.getMapper(OffMessageRecipientMapper.class);
        var converter = Mappers.getMapper(OffMessagePoConverter.class);
        var observed = new CopyOnWriteArrayList<HttpHeaders>();
        var rejected = new CopyOnWriteArrayList<Integer>();
        try (var office = new AnnotationConfigApplicationContext();
                var gateway = new AnnotationConfigApplicationContext();
                var validator = new LocalValidatorFactoryBean()) {
            validator.afterPropertiesSet();
            var registry = new MessageSocketRegistry();
            var service = new OffMessageService(
                    new OffMessageRepositoryImpl(messages, recipients, converter),
                    new OffMessageRecipientRepositoryImpl(
                            recipients, Mappers.getMapper(OffMessageRecipientPoConverter.class)),
                    Mappers.getMapper(OffMessageAssembler.class),
                    new MessageDeliveryRepositoryImpl(
                            messages,
                            recipients,
                            converter,
                            new DataSourceTransactionManager(source),
                            sql.getMapper(OffEmployeeMapper.class)),
                    new OffMessageDeliveryRepositoryImpl(
                            sql.getMapper(OffMessageDeliveryMapper.class),
                            Mappers.getMapper(OffMessageDeliveryPoConverter.class)),
                    office);
            office.register(MessageWebSocketTest.WebConfig.class, MessageWebSocketConfig.class);
            office.registerBean(MessageSocketRegistry.class, () -> registry);
            office.registerBean("messageRoutes", RouterFunction.class, () -> new MessageRouter()
                    .messageRoutes(new MessageHandler(service, new RequestHandlerUtil(validator))));
            office.registerBean(
                    IdentityRelayAuthenticationFilter.class,
                    () -> new IdentityRelayAuthenticationFilter("", SECRET, registry, List.of(ORIGIN)));
            office.registerBean(
                    "observeInboundHeaders", WebFilter.class, () -> new InboundObserver(observed, rejected));
            office.registerBean(SecurityWebFilterChain.class, () -> ServerHttpSecurity.http()
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .authorizeExchange(exchanges -> exchanges.anyExchange().authenticated())
                    .build());
            office.refresh();
            var officeServer = bind(office);
            try {
                configureGateway(gateway, officeServer.port());
                var gatewayServer = bind(gateway);
                try {
                    verifyChain(gatewayServer.port(), registry, jdbc, observed, rejected);
                } finally {
                    gatewayServer.disposeNow();
                }
            } finally {
                officeServer.disposeNow();
            }
        }
    }

    /** 装配真实网关自动配置、生产身份过滤器和指向同一办公端口的 HTTP/WS 路由。 */
    private void configureGateway(AnnotationConfigApplicationContext context, int officePort) {
        context.register(GatewayConfig.class);
        context.registerBean(ServerProperties.class, ServerProperties::new);
        context.registerBean(
                org.springframework.boot.autoconfigure.web.reactive.WebFluxProperties.class,
                org.springframework.boot.autoconfigure.web.reactive.WebFluxProperties::new);
        var properties = new JwksProperties();
        properties.setHmacSecret(SECRET);
        context.registerBean(
                JwtSignatureFilter.class, () -> new JwtSignatureFilter(new GatewayJwtVerifierService(properties)));
        context.registerBean(UserContextRelayFilter.class, UserContextRelayFilter::new);
        context.registerBean(RouteLocator.class, () -> context.getBean(RouteLocatorBuilder.class)
                .routes()
                .route("office-message-websocket", route -> route.order(-1)
                        .path(BASE + "/ws")
                        .and()
                        .header("Upgrade", "(?i)websocket")
                        .uri("ws://127.0.0.1:" + officePort))
                .route("office-message-http", route -> route.path(BASE, BASE + "/**")
                        .uri("http://127.0.0.1:" + officePort))
                .build());
        context.refresh();
    }

    /** 所有客户端请求只访问网关；办公入站观察仅用于断言过滤器的真实转发结果。 */
    private void verifyChain(
            int port,
            MessageSocketRegistry registry,
            JdbcTemplate jdbc,
            List<HttpHeaders> observed,
            List<Integer> rejected) {
        var http = WebClient.create("http://127.0.0.1:" + port);
        assertThat(http.post()
                        .uri(BASE + "/ws-ticket")
                        .headers(this::forgeHeaders)
                        .exchangeToMono(
                                response -> Mono.just(response.statusCode().value()))
                        .block(TIMEOUT))
                .isEqualTo(401);
        assertThat(observed).isEmpty();
        var ticket = request(http, HttpMethod.POST, "/ws-ticket", 7, null)
                .path("ticket")
                .asText();
        assertThat(ticket).isNotBlank();
        var ticketHeaders = observed.get(observed.size() - 1);
        assertThat(ticketHeaders.getFirst("X-User-Id")).isEqualTo("7");
        assertThat(ticketHeaders.getFirst("X-Tenant-Id")).isEqualTo("a");
        assertThat(ticketHeaders.getFirst("X-Client-Type")).isEqualTo("web");
        assertStripped(ticketHeaders);
        var uri = URI.create("ws://127.0.0.1:" + port + BASE + "/ws?ticket=" + ticket);
        var headers = new HttpHeaders();
        forgeHeaders(headers);
        headers.setOrigin("https://forged.example");
        assertRejectedHandshake(uri, headers);
        assertThat(rejected).containsExactly(403);
        assertThat(registry.connectionCount()).isZero();
        headers.setOrigin(ORIGIN);
        new ReactorNettyWebSocketClient()
                .execute(
                        uri,
                        headers,
                        session -> Mono.when(
                                Mono.defer(() -> registry.connectionCount() == 1 ? Mono.just(true) : Mono.empty())
                                        .repeatWhenEmpty(repeat -> repeat.delayElements(Duration.ofMillis(10)))
                                        .then(Mono.fromRunnable(() -> request(
                                                        http,
                                                        HttpMethod.POST,
                                                        "",
                                                        9,
                                                        Map.of(
                                                                "recipientIds",
                                                                List.of(7),
                                                                "subject",
                                                                "网关联合消息",
                                                                "content",
                                                                "持久正文")))
                                                .subscribeOn(BlockingIoSchedulers.remote())),
                                session.receive()
                                        .next()
                                        .map(frame -> frame.getPayloadAsText())
                                        .switchIfEmpty(Mono.error(new AssertionError("必须收到提交提示")))
                                        .publishOn(BlockingIoSchedulers.remote())
                                        .doOnNext(payload -> {
                                            assertThat(payload).contains("\"type\":\"message\"");
                                            assertThat(jdbc.queryForObject(
                                                            "SELECT COUNT(*) FROM off_message", Integer.class))
                                                    .isEqualTo(1);
                                            assertThat(jdbc.queryForObject(
                                                            "SELECT COUNT(*) FROM off_message_recipient",
                                                            Integer.class))
                                                    .isEqualTo(1);
                                            assertThat(request(http, HttpMethod.GET, "/inbox", 7, null)
                                                            .get(0)
                                                            .path("subject")
                                                            .asText())
                                                    .isEqualTo("网关联合消息");
                                            assertThat(request(http, HttpMethod.GET, "/unread/count", 7, null)
                                                            .asInt())
                                                    .isEqualTo(1);
                                        })
                                        .then()))
                .block(TIMEOUT);
        assertThat(observed.stream().filter(value -> "websocket".equalsIgnoreCase(value.getUpgrade())))
                .hasSize(2)
                .allSatisfy(value -> {
                    assertThat(value.getFirst("X-User-Id")).isNull();
                    assertThat(value.getFirst("X-Tenant-Id")).isNull();
                    assertThat(value.getFirst("X-Client-Type")).isNull();
                    assertStripped(value);
                });
        assertRejectedHandshake(uri, headers);
        assertThat(rejected).containsExactly(403, 401);
        assertThat(request(http, HttpMethod.GET, "/inbox", 8, null)).isEmpty();
    }

    /** 网关可能先完成客户端升级；等待连接关闭并确认没有收到任何业务帧。 */
    private void assertRejectedHandshake(URI uri, HttpHeaders headers) {
        new ReactorNettyWebSocketClient()
                .execute(uri, headers, session -> session.receive()
                        .doOnNext(frame -> {
                            throw new AssertionError("被拒绝的握手不得收到业务帧");
                        })
                        .then())
                .block(TIMEOUT);
    }

    /** 在认证之前观察真实入站头及最终拒绝状态，不改变请求处理结果。 */
    private record InboundObserver(List<HttpHeaders> observed, List<Integer> rejected)
            implements WebFilter, org.springframework.core.Ordered {
        @Override
        public int getOrder() {
            return HIGHEST_PRECEDENCE;
        }

        @Override
        public Mono<Void> filter(
                org.springframework.web.server.ServerWebExchange exchange,
                org.springframework.web.server.WebFilterChain chain) {
            var copy = new HttpHeaders();
            copy.addAll(exchange.getRequest().getHeaders());
            observed.add(copy);
            exchange.getResponse().beforeCommit(() -> {
                var status = exchange.getResponse().getStatusCode();
                if (status != null && status.isError()) {
                    rejected.add(status.value());
                }
                return Mono.empty();
            });
            return chain.filter(exchange);
        }
    }

    /** 校验内部身份及事务上下文未到达办公应用。 */
    private void assertStripped(HttpHeaders headers) {
        assertThat(headers.keySet()).noneMatch(name -> name.regionMatches(true, 0, "X-Internal-", 0, 11));
        assertThat(headers.getFirst("X-Apex-Seata-Xid")).isNull();
        assertThat(headers.getFirst("X-Apex-Seata-Key")).isNull();
        assertThat(headers.getFirst("TX_XID")).isNull();
    }

    /** 注入恶意身份与内部上下文，验证真实网关入口的清洗行为。 */
    private void forgeHeaders(HttpHeaders headers) {
        headers.set("X-User-Id", "888");
        headers.set("X-Tenant-Id", "forged");
        headers.set("X-Client-Type", "forged");
        headers.set("X-Internal-User", "forged");
        headers.set("X-Apex-Seata-Xid", "forged");
        headers.set("X-Apex-Seata-Key", "forged");
        headers.set("TX_XID", "forged");
    }

    /** 使用测试签发的访问 JWT 经真实网关请求业务接口。 */
    private JsonNode request(WebClient http, HttpMethod method, String path, long user, Object body) {
        var token = Jwts.builder()
                .claim("userId", user)
                .claim("tenantId", "a")
                .claim("clientType", "web")
                .claim("tokenType", "ACCESS")
                .expiration(Date.from(Instant.now().plusSeconds(120)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        var spec = http.method(method).uri(BASE + path).headers(headers -> {
            forgeHeaders(headers);
            headers.setBearerAuth(token);
        });
        var response = (body == null
                        ? spec
                        : spec.contentType(MediaType.APPLICATION_JSON).bodyValue(body))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(TIMEOUT);
        assertThat(response).isNotNull();
        assertThat(response.path("code").asInt()).isZero();
        return response.path("data");
    }

    /** 将 Spring WebFlux 处理链绑定到独立随机 Netty 端口。 */
    private DisposableServer bind(AnnotationConfigApplicationContext context) {
        return HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle(new ReactorHttpHandlerAdapter(
                        WebHttpHandlerBuilder.applicationContext(context).build()))
                .bindNow();
    }

    /**
     * 仅加载真实网关路由基础设施，避免依赖 Nacos、Redis 或访问日志数据库。
     * @author Apex Team
     * @version 1.3.1
     * @since 1.3.1
     */
    @Configuration
    @EnableWebFlux
    @Import({GatewayAutoConfiguration.class, org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration.class})
    static class GatewayConfig {}
}
