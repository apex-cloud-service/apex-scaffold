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
 * 校验三件事：站内信 WS/HTTP 路由存在、均排在 {@code office-domain} 通配路由之前、WS 路由为 {@code ws}/{@code wss}
 * 语义且不挂 HTTP 熔断回退。测试不启动 Spring 上下文、不依赖 Nacos 或下游服务。
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

    /** 站内信 WebSocket 握手路径。 */
    private static final String WEBSOCKET_PATH = "/api/v1/messages/ws";

    /** 熔断回退目标，与网关全局回退约定一致。 */
    private static final String FALLBACK_URI = "forward:/__gateway/fallback";

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

        // 办公域通配路由必须同时覆盖 v1 与 v2（组织同步向导）前缀，防止整表重定义时 v2 路径丢失。
        assertThat(pathPredicateArgs(office))
                .withFailMessage("%s：office-domain 须覆盖 v1/v2 办公前缀", source)
                .contains("/api/v1/office/**", "/api/v2/office/**");
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
        return routes.stream()
                .filter(route -> routeId.equals(route.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(source + "：缺少网关路由 " + routeId));
    }
}
