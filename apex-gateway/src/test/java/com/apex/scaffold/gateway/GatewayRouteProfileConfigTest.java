package com.apex.scaffold.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/**
 * 网关站内信路由配置契约测试.
 *
 * <p>背景：网关的 {@code spring.cloud.gateway.routes} 是 YAML 列表，列表不逐元素合并。
 * {@code application-prod.yml} 与 {@code config/nacos/apex-gateway.yaml} 各自重定义了整张路由表，
 * 因此仅在 {@code application.yml} 中声明站内信路由，在启用 prod profile 或 Nacos 配置时会被整表覆盖而丢失。
 *
 * <p>丢失后的表现是隐蔽的：{@code /api/v1/messages/ws} 会落到 {@code office-domain} 的
 * {@code lb://}（http 语义）路由上，Spring Cloud Gateway 的 WebSocket 过滤器只处理 {@code ws}/{@code wss}
 * 语义的 URI，升级握手因此被丢弃，HTTP 消息接口却仍然正常，问题只在长连接侧暴露。
 *
 * <p>本测试对每个配置源单独绑定路由列表（不跨文件合并，以忠实反映各文件自身声明的内容），
 * 校验域路由契约：站内信 WS/HTTP 路由存在且排在 {@code office-domain} 之前；认证域覆盖认证中心
 * 契约路径且不承接由 system 承载的 {@code /admin-api/v1/auth|sso/**}；办公域覆盖 {@code /api/office/**}；
 * 文件域覆盖管理端图表前缀；系统域兜底显式覆盖 {@code /api/v2/operations/**} 与 {@code /api/v2/platform/**}；
 * job/masking 独立路由在整表重定义的配置源中显式声明。测试不启动 Spring 上下文、不依赖 Nacos 或下游服务。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("网关站内信路由配置契约测试")
class GatewayRouteProfileConfigTest {

    /** 基础配置文件，dev 与默认场景生效。 */
    private static final String BASE_CONFIG = "application.yml";

    /** 生产 profile 配置文件，整表重定义路由。 */
    private static final String PROD_CONFIG = "application-prod.yml";

    /** Nacos 下发文件路径，以网关模块目录为基准指向仓库根 config 目录。 */
    private static final String NACOS_CONFIG = "../config/nacos/apex-gateway.yaml";

    /** 站内信 WebSocket 握手路由标识。 */
    private static final String WEBSOCKET_ROUTE_ID = "office-message-websocket";

    /** 站内信 HTTP 路由标识。 */
    private static final String HTTP_ROUTE_ID = "office-message-http";

    /** 办公域通配路由标识，站内信路由必须排在它之前。 */
    private static final String OFFICE_ROUTE_ID = "office-domain";

    /** 认证域路由标识：基础与生产配置为 auth-service，Nacos 配置为 auth-domain。 */
    private static final List<String> AUTH_ROUTE_IDS = List.of("auth-service", "auth-domain");

    /** 文件域路由标识，须显式覆盖管理端图表前缀。 */
    private static final String FILE_ROUTE_ID = "file-domain";

    /** 系统域兜底路由标识，须显式覆盖体检运营与平台租户接口前缀。 */
    private static final String SYSTEM_ROUTE_ID = "system-domain";

    /** 工作流域路由标识，须显式覆盖流程定义生命周期 v2 前缀。 */
    private static final String WORKFLOW_ROUTE_ID = "workflow-domain";

    /** 任务代理路由标识，整表重定义时易被整表覆盖丢失。 */
    private static final String JOB_ROUTE_ID = "job-domain";

    /** 脱敏服务路由标识，整表重定义时易被整表覆盖丢失。 */
    private static final String MASKING_ROUTE_ID = "masking-domain";

    /** 集成中心路由标识（E1-E3 管理端点），整表重定义时易被整表覆盖丢失。 */
    private static final String INTEGRATION_ROUTE_ID = "integration-domain";

    /** 站内信 WebSocket 握手路径。 */
    private static final String WEBSOCKET_PATH = "/api/v1/messages/ws";

    /** 熔断回退目标，与网关全局回退约定一致。 */
    private static final String FALLBACK_URI = "forward:/__gateway/fallback";

    /** 开放接口路由标识（D2/D4）：整表重定义的配置源必须显式保留。 */
    private static final List<String> OPEN_API_ROUTE_IDS =
            List.of("open-api-file", "open-api-workflow", "open-api-system", "open-api-office");

    /** 路由元数据键：scope/能力断言契约 {@code metadata.open-api}。 */
    private static final String OPEN_API_METADATA_KEY = "open-api";

    /**
     * 开放接口路由契约条目：Path 前缀 + 路由元数据 {@code open-api} 节.
     *
     * <p>空 scope 清单表示对应方法族一律拒绝（如 user 写接口、office 读接口不开放）。
     */
    private record OpenApiContract(
            List<String> paths, String capability, List<String> readScopes, List<String> writeScopes) {}

    /**
     * 开放接口路由契约（D2/D4）：整表重定义的配置源必须逐条保留，否则
     * {@code /open-api/**} 会落入系统域兜底路由并绕过 scope/能力门禁。
     */
    private static final java.util.Map<String, OpenApiContract> OPEN_API_CONTRACT = java.util.Map.of(
            "open-api-file",
            new OpenApiContract(
                    List.of("/open-api/file/**", "/open-api/files/**"),
                    "file-storage",
                    List.of("open:file:read"),
                    List.of("open:file:write")),
            "open-api-workflow",
            new OpenApiContract(
                    List.of("/open-api/workflow/**"),
                    "workflow",
                    List.of("open:workflow:read"),
                    List.of("open:workflow:write")),
            "open-api-system",
            new OpenApiContract(
                    List.of("/open-api/users/**", "/open-api/user/**"),
                    "user-org",
                    List.of("open:user:read"),
                    List.of()),
            "open-api-office",
            new OpenApiContract(
                    List.of("/open-api/messages/**"), "office-message", List.of(), List.of("open:message:send")));

    @Test
    @DisplayName("application.yml 应声明站内信路由且 WS 优先于办公域通配路由")
    void baseConfigKeepsMessageRoutes() {
        assertMessageRoutesPrecedeOfficeDomain(routesFrom(new ClassPathResource(BASE_CONFIG)), BASE_CONFIG);
    }

    @Test
    @DisplayName("application-prod.yml 整表重定义后仍应保留站内信路由且 WS 优先")
    void prodConfigKeepsMessageRoutes() {
        assertMessageRoutesPrecedeOfficeDomain(routesFrom(new ClassPathResource(PROD_CONFIG)), PROD_CONFIG);
    }

    @Test
    @DisplayName("Nacos 下发的网关配置应保留站内信路由且 WS 优先")
    void nacosConfigKeepsMessageRoutes() {
        Path path = Path.of(NACOS_CONFIG).toAbsolutePath().normalize();
        assertThat(Files.exists(path))
                .withFailMessage("未找到 Nacos 网关配置，路径解析可能已失效：%s", path)
                .isTrue();
        assertMessageRoutesPrecedeOfficeDomain(routesFrom(new FileSystemResource(path)), NACOS_CONFIG);
    }

    /**
     * 校验单个配置源中的站内信路由契约.
     *
     * @param routes 该配置源绑定的路由定义，按文件声明顺序排列
     * @param source 配置源名称，仅用于失败信息
     */
    private static void assertMessageRoutesPrecedeOfficeDomain(List<RouteDefinition> routes, String source) {
        RouteDefinition websocket = findRoute(routes, WEBSOCKET_ROUTE_ID, source);
        RouteDefinition http = findRoute(routes, HTTP_ROUTE_ID, source);
        RouteDefinition office = findRoute(routes, OFFICE_ROUTE_ID, source);

        assertThat(routes.indexOf(websocket))
                .withFailMessage("%s：WS 路由须排在 %s 之前", source, OFFICE_ROUTE_ID)
                .isLessThan(routes.indexOf(office));
        assertThat(routes.indexOf(http))
                .withFailMessage("%s：消息 HTTP 路由须排在 %s 之前", source, OFFICE_ROUTE_ID)
                .isLessThan(routes.indexOf(office));

        assertThat(websocket.getUri().getScheme())
                .withFailMessage("%s：WS 路由须为 ws/wss 语义，否则升级握手会被丢弃", source)
                .isIn("ws", "wss");
        assertThat(pathPredicateArgs(websocket)).contains(WEBSOCKET_PATH);
        assertThat(websocket.getFilters())
                .withFailMessage("%s：WS 路由不得挂 HTTP 熔断回退", source)
                .noneSatisfy(filter -> assertThat(filter.getName()).isEqualTo("CircuitBreaker"));

        assertThat(http.getUri().getScheme()).isIn("http", "https");
        assertThat(pathPredicateArgs(http)).contains("/api/v1/messages", "/api/v1/messages/**");
        assertThat(http.getFilters()).anySatisfy(filter -> {
            assertThat(filter.getName()).isEqualTo("CircuitBreaker");
            assertThat(filter.getArgs())
                    .containsEntry("name", "officeService")
                    .containsEntry("fallbackUri", FALLBACK_URI);
        });

        // 办公域通配路由必须同时覆盖 v1 与 v2（组织同步向导）前缀，防止整表重定义时 v2 路径丢失；
        // 同时覆盖 /api/office/**（站内信 HTTP 消息接口）与管理端消息模板前缀。
        assertThat(pathPredicateArgs(office))
                .withFailMessage("%s：office-domain 须覆盖 v1/v2 办公前缀", source)
                .contains("/api/v1/office/**", "/api/v2/office/**")
                .withFailMessage("%s：office-domain 须覆盖 /api/office/** 与管理端消息模板前缀", source)
                .contains("/api/office/**", "/admin-api/v1/message-templates");

        // 认证域路由：auth-service 承载认证协议、密码找回与认证中心管理面（MFA/IP 访问控制/
        // 认证配置/审计日志）；/admin-api/v1/auth|sso/** 由 system 承载，若误加入本路由
        // 会在 auth-service 404。
        RouteDefinition auth = findRouteAny(routes, AUTH_ROUTE_IDS, source);
        assertThat(pathPredicateArgs(auth))
                .withFailMessage("%s：auth 路由须覆盖认证中心契约路径", source)
                .contains(
                        "/auth/v1/**",
                        "/auth/v2/**",
                        "/oauth2/**",
                        "/.well-known/**",
                        "/api/v1/mfa/**",
                        "/api/mfa/**",
                        "/api/ip-access-control/**",
                        "/api/v1/auth/configs/**",
                        "/api/v1/auth/audit-logs/**")
                .withFailMessage("%s：auth 路由不得承接 /admin-api/v1/auth|sso/**（由 system 承载）", source)
                .doesNotContain("/admin-api/v1/auth/**", "/admin-api/v1/sso/**");

        // 文件域路由必须显式覆盖管理端图表前缀（ChartRouter），否则落入 system 兜底后 404。
        assertThat(pathPredicateArgs(findRoute(routes, FILE_ROUTE_ID, source)))
                .withFailMessage("%s：file-domain 须覆盖 /admin-api/v1/charts/** 前缀", source)
                .contains("/admin-api/v1/charts/**");

        // 工作流域路由必须显式覆盖定义生命周期 v2 前缀（WorkflowDefinitionRouter），
        // 否则 Nacos 整表重定义或生产配置下定义端点落入 system 兜底后 404。
        assertThat(pathPredicateArgs(findRoute(routes, WORKFLOW_ROUTE_ID, source)))
                .withFailMessage("%s：workflow-domain 须覆盖 /api/v2/workflow-definitions/** 前缀", source)
                .contains("/api/v2/workflow-definitions/**");

        // 系统域兜底路由必须显式覆盖 /api/v2/operations/**（体检快照、趋势、导出、租户摘要）
        // 与 /api/v2/platform/**（平台租户管理端点），否则整表重定义后相应端点无显式路由。
        RouteDefinition system = findRoute(routes, SYSTEM_ROUTE_ID, source);
        assertThat(pathPredicateArgs(system))
                .withFailMessage("%s：system-domain 须覆盖 /api/v2/operations/** 前缀", source)
                .contains("/api/v2/operations/**")
                .withFailMessage("%s：system-domain 须覆盖 /api/v2/platform/** 前缀", source)
                .contains("/api/v2/platform/**");

        // 独立轻量服务路由（job/masking）在整表重定义的配置源中必须显式声明并挂熔断回退。
        for (String routeId : List.of(JOB_ROUTE_ID, MASKING_ROUTE_ID)) {
            RouteDefinition lightweight = findRoute(routes, routeId, source);
            assertThat(lightweight.getUri().getScheme()).isIn("http", "https");
            assertThat(lightweight.getFilters()).anySatisfy(filter -> {
                assertThat(filter.getName()).isEqualTo("CircuitBreaker");
                assertThat(filter.getArgs()).containsEntry("fallbackUri", FALLBACK_URI);
            });
        }
        assertThat(pathPredicateArgs(findRoute(routes, JOB_ROUTE_ID, source)))
                .withFailMessage("%s：job-domain 须覆盖任务代理前缀", source)
                .contains("/admin-api/v1/jobs", "/admin-api/v1/jobs/**");
        assertThat(pathPredicateArgs(findRoute(routes, MASKING_ROUTE_ID, source)))
                .withFailMessage("%s：masking-domain 须覆盖脱敏前缀", source)
                .contains("/api/v1/masking/**");

        // 集成中心（E1-E3）：管理端点须有独立路由，且不得把内部端点 /internal/v1/** 暴露到网关
        RouteDefinition integration = findRoute(routes, INTEGRATION_ROUTE_ID, source);
        assertThat(pathPredicateArgs(integration))
                .withFailMessage("%s：integration-domain 须覆盖集成与 IoT 管理前缀", source)
                .contains("/integration/v1/**")
                .withFailMessage("%s：integration-domain 不得暴露内部端点", source)
                .noneMatch(path -> path.startsWith("/internal/"));
        assertThat(integration.getFilters())
                .withFailMessage("%s：integration-domain 须挂熔断回退", source)
                .anySatisfy(filter -> {
                    assertThat(filter.getName()).isEqualTo("CircuitBreaker");
                    assertThat(filter.getArgs()).containsEntry("fallbackUri", FALLBACK_URI);
                });

        assertOpenApiRoutes(routes, source);
    }

    /**
     * 校验单个配置源中的开放接口路由契约（D2/D4）.
     *
     * <p>逐条断言：路由存在、Path 前缀完整、{@code metadata.open-api} 的 capability 与
     * read/write-scopes 与契约一致（含空清单语义），且挂有熔断回退。
     *
     * @param routes 该配置源绑定的路由定义
     * @param source 配置源名称，仅用于失败信息
     */
    private static void assertOpenApiRoutes(List<RouteDefinition> routes, String source) {
        for (String routeId : OPEN_API_ROUTE_IDS) {
            RouteDefinition route = findRoute(routes, routeId, source);
            OpenApiContract contract = OPEN_API_CONTRACT.get(routeId);

            assertThat(pathPredicateArgs(route))
                    .withFailMessage("%s：%s 须覆盖开放接口前缀", source, routeId)
                    .containsAll(contract.paths());
            assertThat(route.getFilters())
                    .withFailMessage("%s：%s 须挂熔断回退", source, routeId)
                    .anySatisfy(filter -> {
                        assertThat(filter.getName()).isEqualTo("CircuitBreaker");
                        assertThat(filter.getArgs()).containsEntry("fallbackUri", FALLBACK_URI);
                    });

            assertThat(route.getMetadata().get(OPEN_API_METADATA_KEY))
                    .withFailMessage("%s：%s 须声明 metadata.%s 节", source, routeId, OPEN_API_METADATA_KEY)
                    .isInstanceOf(java.util.Map.class);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> metadata =
                    (java.util.Map<String, Object>) route.getMetadata().get(OPEN_API_METADATA_KEY);

            assertThat(metadata.get("capability"))
                    .withFailMessage("%s：%s 的 capability 契约不一致", source, routeId)
                    .isEqualTo(contract.capability());
            assertThat(scopeList(metadata.get("read-scopes")))
                    .withFailMessage("%s：%s 的 read-scopes 契约不一致 actual=%s", source, routeId, metadata)
                    .containsExactlyInAnyOrderElementsOf(contract.readScopes());
            assertThat(scopeList(metadata.get("write-scopes")))
                    .withFailMessage("%s：%s 的 write-scopes 契约不一致 actual=%s", source, routeId, metadata)
                    .containsExactlyInAnyOrderElementsOf(contract.writeScopes());
        }
    }

    /**
     * 归一化路由元数据中的 scope 清单.
     *
     * <p>Spring Boot Binder 将 Object 值下的 YAML 列表绑定为 {@code {0=v, 1=v}} 形式的 Map，
     * 与 {@code OpenApiScopeGatewayFilter.OpenApiMetadata#scopeList} 的兼容口径一致。
     *
     * @param raw 元数据原始值
     * @return scope 字符串列表
     */
    private static List<String> scopeList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (raw instanceof java.util.Map<?, ?> map) {
            return map.values().stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    /**
     * 提取路由 Path 谓词的路径集合.
     *
     * @param route 路由定义
     * @return Path 谓词中的路径取值
     */
    private static List<String> pathPredicateArgs(RouteDefinition route) {
        return route.getPredicates().stream()
                .filter(predicate -> "Path".equals(predicate.getName()))
                .flatMap(predicate -> predicate.getArgs().values().stream())
                .toList();
    }

    /**
     * 绑定单个配置文件的路由列表.
     *
     * <p>每次调用使用独立环境，只加载一个文件，避免多个 YAML 的列表按下标互相串写而掩盖真实声明内容。
     *
     * @param resource 配置文件资源
     * @return 绑定后的路由定义，声明顺序与文件一致
     */
    private static List<RouteDefinition> routesFrom(Resource resource) {
        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        try {
            for (PropertySource<?> source : loader.load(resource.getFilename(), resource)) {
                environment.getPropertySources().addLast(source);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法加载网关配置：" + resource, exception);
        }
        return Binder.get(environment)
                .bind("spring.cloud.gateway.routes", Bindable.listOf(RouteDefinition.class))
                .orElseGet(List::of);
    }

    /**
     * 按路由标识查找路由，缺失时直接失败.
     *
     * @param routes 路由定义
     * @param routeId 路由标识
     * @param source 配置源名称，仅用于失败信息
     * @return 命中的路由定义
     */
    private static RouteDefinition findRoute(List<RouteDefinition> routes, String routeId, String source) {
        return findRouteAny(routes, List.of(routeId), source);
    }

    /**
     * 按候选路由标识依次查找路由（同一逻辑域在不同配置源的 id 可能不同），全部缺失时失败.
     *
     * @param routes 路由定义
     * @param routeIds 候选路由标识
     * @param source 配置源名称，仅用于失败信息
     * @return 命中的路由定义
     */
    private static RouteDefinition findRouteAny(List<RouteDefinition> routes, List<String> routeIds, String source) {
        return routes.stream()
                .filter(route -> routeIds.contains(route.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(source + "：缺少网关路由 " + routeIds));
    }
}
