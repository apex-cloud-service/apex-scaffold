package com.apex.scaffold.officeservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.context.TenantContext;
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
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
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
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;

/**
 * 使用真实 HTTP、MyBatis Mapper、H2 事务及 WebSocket 验证站内信闭环。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
class MessageDatabaseWebSocketTest {
    private static final String SECRET = "office-test-key-0123456789-abcdef0123456789";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /** 在线提示可立即读取已提交数据，断线期间的消息通过收件箱与未读接口补偿。 */
    @Test
    void deliversCommittedMessageAndRecoversOfflineInbox() throws Exception {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/basic-schema.sql")).execute(source);
        var jdbc = new JdbcTemplate(source);
        jdbc.update(
                "INSERT INTO off_employee(employee_code,employee_name,dept_id,user_id,tenant_id) VALUES ('e7','接收人',1,7,'a')");
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
        try (var context = new AnnotationConfigApplicationContext()) {
            var registry = new MessageSocketRegistry();
            var validator = new LocalValidatorFactoryBean();
            validator.afterPropertiesSet();
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
                    context);
            context.register(MessageWebSocketTest.WebConfig.class, MessageWebSocketConfig.class);
            context.registerBean(MessageSocketRegistry.class, () -> registry);
            context.registerBean("messageRoutes", RouterFunction.class, () -> new MessageRouter()
                    .messageRoutes(new MessageHandler(service, new RequestHandlerUtil(validator))));
            context.registerBean(
                    IdentityRelayAuthenticationFilter.class,
                    () -> IdentityRelayAuthenticationFilter.forTesting(
                            "", SECRET, registry, List.of("https://app.example")));
            context.registerBean(SecurityWebFilterChain.class, () -> ServerHttpSecurity.http()
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .authorizeExchange(exchanges -> exchanges.anyExchange().authenticated())
                    .build());
            context.refresh();
            var server = HttpServer.create()
                    .host("127.0.0.1")
                    .port(0)
                    .handle(new ReactorHttpHandlerAdapter(
                            WebHttpHandlerBuilder.applicationContext(context).build()))
                    .bindNow();
            try {
                var http = WebClient.create("http://127.0.0.1:" + server.port());
                var ticket = request(http, HttpMethod.POST, "/ws-ticket", 7, null)
                        .path("ticket")
                        .asText();
                var headers = new HttpHeaders();
                headers.setOrigin("https://app.example");
                new ReactorNettyWebSocketClient()
                        .execute(
                                URI.create("ws://127.0.0.1:" + server.port() + "/api/v1/messages/ws?ticket=" + ticket),
                                headers,
                                session -> Mono.when(
                                        Mono.defer(() -> registry.connectionCount() == 1
                                                        ? Mono.just(true)
                                                        : Mono.empty())
                                                .repeatWhenEmpty(repeat -> repeat.delayElements(Duration.ofMillis(10)))
                                                .then(
                                                        Mono.fromRunnable(() -> {
                                                                    verifyRollback(http, jdbc, registry);
                                                                    send(http, "在线");
                                                                })
                                                                .subscribeOn(com.apex.scaffold.common.scheduler
                                                                        .BlockingIoSchedulers.remote())),
                                        session.receive()
                                                .next()
                                                .map(frame -> frame.getPayloadAsText())
                                                .switchIfEmpty(Mono.error(new AssertionError("必须收到消息提示")))
                                                .publishOn(
                                                        com.apex.scaffold.common.scheduler.BlockingIoSchedulers.jdbc())
                                                .doOnNext(payload -> {
                                                    assertThat(payload).contains("\"type\":\"message\"");
                                                    assertThat(jdbc.queryForObject(
                                                                    "SELECT COUNT(*) FROM off_message", Integer.class))
                                                            .isEqualTo(1);
                                                    assertThat(jdbc.queryForObject(
                                                                    "SELECT COUNT(*) FROM off_message_recipient",
                                                                    Integer.class))
                                                            .isEqualTo(1);
                                                })
                                                .then()))
                        .block(TIMEOUT);
                assertThat(request(http, HttpMethod.GET, "/unread/count", 7, null)
                                .asInt())
                        .isEqualTo(1);
                long first = request(http, HttpMethod.GET, "/inbox", 7, null)
                        .get(0)
                        .path("id")
                        .asLong();
                request(http, HttpMethod.PUT, "/" + first + "/read", 7, null);
                assertThat(request(http, HttpMethod.GET, "/unread/count", 7, null)
                                .asInt())
                        .isZero();
                assertThat(jdbc.queryForObject(
                                "SELECT read_time FROM off_message_recipient WHERE message_id=?",
                                java.sql.Timestamp.class,
                                first))
                        .isNotNull();
                Mono.defer(() -> registry.connectionCount() == 0 ? Mono.just(true) : Mono.empty())
                        .repeatWhenEmpty(repeat -> repeat.delayElements(Duration.ofMillis(10)))
                        .block(TIMEOUT);
                send(http, "离线补偿");
                var inbox = request(http, HttpMethod.GET, "/inbox", 7, null);
                assertThat(inbox.size()).isEqualTo(2);
                assertThat(inbox.get(0).path("subject").asText()).isEqualTo("离线补偿");
                assertThat(request(http, HttpMethod.GET, "/unread/count", 7, null)
                                .asInt())
                        .isEqualTo(1);
                assertThat(request(http, HttpMethod.GET, "/inbox", 8, null)).isEmpty();
                request(http, HttpMethod.PUT, "/read-all", 7, null);
                assertThat(request(http, HttpMethod.GET, "/unread/count", 7, null)
                                .asInt())
                        .isZero();
            } finally {
                server.disposeNow();
                validator.close();
            }
        }
    }

    /** 用真实数据库约束使第二个收件人失败，验证主体与首个收件人均回滚且无提交事件。 */
    private void verifyRollback(WebClient http, JdbcTemplate jdbc, MessageSocketRegistry registry) {
        jdbc.update(
                "INSERT INTO off_employee(employee_code,employee_name,dept_id,user_id,tenant_id) VALUES ('e8','失败接收人',1,8,'a')");
        jdbc.execute("ALTER TABLE off_message_recipient ADD CONSTRAINT reject_eight CHECK (recipient_id <> 8)");
        var probe = registry.connect(new AuthenticatedUser(7L, null, "a"));
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> request(
                            http,
                            HttpMethod.POST,
                            "",
                            9,
                            Map.of("recipientIds", List.of(7, 8), "subject", "回滚", "content", "约束失败")))
                    .isInstanceOf(org.springframework.web.reactive.function.client.WebClientResponseException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM off_message", Integer.class))
                    .isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM off_message_recipient", Integer.class))
                    .isZero();
            assertThat(probe.queue()).isEmpty();
        } finally {
            registry.disconnect(probe);
            jdbc.execute("ALTER TABLE off_message_recipient DROP CONSTRAINT reject_eight");
        }
    }

    /** 使用真实发送接口提交消息，校验统一成功码及数据库主键。 */
    private void send(WebClient http, String subject) {
        assertThat(request(
                                http,
                                HttpMethod.POST,
                                "",
                                9,
                                Map.of("recipientIds", List.of(7), "subject", subject, "content", "持久正文"))
                        .path("id")
                        .asLong())
                .isPositive();
    }

    /** 签发访问 JWT 并调用生产路由，返回成功响应的数据部分。 */
    private JsonNode request(WebClient http, HttpMethod method, String path, long user, Object body) {
        String jwt = Jwts.builder()
                .claim("userId", user)
                .claim("tenantId", "a")
                .claim("tokenType", "ACCESS")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        var spec = http.method(method).uri("/api/v1/messages" + path).headers(headers -> headers.setBearerAuth(jwt));
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
}
