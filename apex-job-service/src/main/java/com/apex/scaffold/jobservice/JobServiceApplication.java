package com.apex.scaffold.jobservice;

import com.apex.scaffold.infrastructure.remote.config.ManagedWebClientFactory;
import io.swagger.v3.oas.annotations.Operation;
import java.net.URI;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 独立任务门面，仅扫描本服务；不装配 system 业务或共享持久化组件。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@SpringBootApplication(
        excludeName = {
            "com.apex.scaffold.infrastructure.persistence.config.MybatisPlusConfig",
            "com.apex.scaffold.infrastructure.cache.config.MultiLevelCacheConfig",
            "com.apex.scaffold.infrastructure.remote.config.RemoteApiClientConfig",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure",
            "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
            "org.redisson.spring.starter.RedissonAutoConfigurationV2",
            "com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration",
            // 本服务为无状态代理：排除共享设施传递引入的 Redis/邮件自动配置，
            // 避免健康指示器在本机缺少 Redis/SMTP 时将 /actuator/health 置为 DOWN。
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
            "org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration"
        })
@org.springframework.context.annotation.Import(
        com.apex.scaffold.infrastructure.remote.config.ManagedWebClientInitializer.class)
public class JobServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobServiceApplication.class, args);
    }

    /** 目标仅来自部署配置，禁止请求参数指定目标地址。 */
    @Bean
    JobProxy jobProxy(
            @Value("${apex.job.system-url:http://127.0.0.1:8082}") String systemUrl, ManagedWebClientFactory factory) {
        URI uri = URI.create(systemUrl);
        if (isInvalidProxyUrl(uri)) {
            throw new IllegalArgumentException("任务代理地址必须是无凭据的固定 HTTP(S) 地址");
        }
        return new JobProxy(factory.create(systemUrl));
    }

    /** 校验代理地址：必须有主机、无用户凭据与查询串，且限定 HTTP(S) 协议。 */
    private static boolean isInvalidProxyUrl(URI uri) {
        boolean hasHost = uri.getHost() != null;
        boolean noCredentials = uri.getUserInfo() == null;
        boolean noQuery = uri.getQuery() == null;
        boolean httpScheme = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
        return !hasHost || !noCredentials || !noQuery || !httpScheme;
    }

    /** 任务权限委托 system 的 system:job:* 校验，原样保留上游错误状态。 */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/jobs",
                method = RequestMethod.GET,
                operation = @Operation(operationId = "listJobs", summary = "查询任务列表")),
        @RouterOperation(
                path = "/admin-api/v1/jobs/page",
                method = RequestMethod.GET,
                operation = @Operation(operationId = "pageJobs", summary = "分页查询任务")),
        @RouterOperation(
                path = "/admin-api/v1/jobs",
                method = RequestMethod.POST,
                operation = @Operation(operationId = "createJob", summary = "创建任务")),
        @RouterOperation(
                path = "/admin-api/v1/jobs/{id}",
                method = RequestMethod.GET,
                operation = @Operation(operationId = "getJob", summary = "查询任务")),
        @RouterOperation(
                path = "/admin-api/v1/jobs/{id}",
                method = RequestMethod.PUT,
                operation = @Operation(operationId = "updateJob", summary = "更新任务")),
        @RouterOperation(
                path = "/admin-api/v1/jobs/{id}",
                method = RequestMethod.DELETE,
                operation = @Operation(operationId = "deleteJob", summary = "删除任务")),
        @RouterOperation(
                path = "/admin-api/v1/jobs/{id}/status",
                method = RequestMethod.PUT,
                operation = @Operation(operationId = "updateJobStatus", summary = "更新任务状态")),
        @RouterOperation(
                path = "/admin-api/v1/jobs/{id}/run",
                method = RequestMethod.POST,
                operation = @Operation(operationId = "runJob", summary = "触发任务")),
        @RouterOperation(
                path = "/admin-api/v1/jobs/{id}/logs",
                method = RequestMethod.GET,
                operation = @Operation(operationId = "jobLogs", summary = "查询任务日志"))
    })
    RouterFunction<ServerResponse> jobRoutes(JobProxy proxy) {
        return proxy.routes();
    }

    /** 无 Cookie 认证；认证在固定上游执行，所有未匹配路由禁止访问。 */
    @Bean
    SecurityWebFilterChain security(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchange -> exchange
                        // 健康检查端点对编排探针开放，不暴露组件明细（由 management 配置控制）。
                        .pathMatchers("/actuator/health")
                        .permitAll()
                        .pathMatchers("/admin-api/v1/jobs", "/admin-api/v1/jobs/**")
                        .permitAll()
                        .anyExchange()
                        .denyAll())
                .build();
    }
}
