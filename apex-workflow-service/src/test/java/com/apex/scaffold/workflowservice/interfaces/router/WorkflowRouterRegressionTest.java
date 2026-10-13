package com.apex.scaffold.workflowservice.interfaces.router;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.workflowservice.interfaces.handler.WorkflowHandler;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.Resource;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 工作流全接口路由回归测试.
 *
 * <p>以「无容器」方式加载 {@link WorkflowRouter} 暴露的全部 {@link RouterFunction} Bean，
 * 通过 {@link RouterFunctions.Visitor} 访问器遍历路由树，收集对外暴露的
 * 「HTTP 方法 + 绝对路径」端点集合，并与预期集合精确比对。该测试覆盖迁移方案
 * §6.2「工作流全接口回归」要求，确保网关切流后 {@code apex-workflow-service}
 * 独立暴露的接口路径与旧单体一致、无遗漏、无错配、无冗余。
 *
 * <h2>覆盖接口（共 12 个）</h2>
 * <ul>
 *   <li>管理后台查询：{@code /admin-api/v1/workflows/**}（4 个）</li>
 *   <li>业务 API：{@code /api/v1/workflows/**}（3 个）</li>
 *   <li>业务操作：{@code /api/workflow/**}（5 个）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@DisplayName("工作流全接口路由回归测试")
class WorkflowRouterRegressionTest {

    /** HTTP 方法与路径提取表达式。 */
    private static final Pattern METHOD_PATTERN = Pattern.compile("\\b(GET|POST|PUT|DELETE|PATCH)\\b");

    /** 路由谓词中的相对路径提取表达式。 */
    private static final Pattern PATH_PATTERN = Pattern.compile("&& (/[^)]+)\\)");

    /** 路由 Bean 工厂方法名称列表。 */
    private static final List<String> ROUTER_FACTORIES =
            List.of("adminWorkflowQueryRoutes", "workflowApiRoutes", "workflowRoutes");

    /**
     * 验证管理后台查询接口全部正确注册。
     */
    @Test
    @DisplayName("管理后台查询接口全部正确注册")
    void shouldExposeAdminQueryEndpoints() {
        Set<Endpoint> expected = Set.of(
                new Endpoint("GET", "/admin-api/v1/workflows/instances"),
                new Endpoint("GET", "/admin-api/v1/workflows/instances/{instanceId}"),
                new Endpoint("GET", "/admin-api/v1/workflows/statistics"),
                new Endpoint("GET", "/admin-api/v1/workflows/statistics/trend"));

        assertThat(collectEndpoints()).containsAll(expected);
    }

    /**
     * 验证业务 API 接口全部正确注册。
     */
    @Test
    @DisplayName("业务 API 接口全部正确注册")
    void shouldExposeWorkflowApiEndpoints() {
        Set<Endpoint> expected = Set.of(
                new Endpoint("POST", "/api/v1/workflows/{workflowCode}/instances"),
                new Endpoint("GET", "/api/v1/workflows/tasks/pending"),
                new Endpoint("POST", "/api/v1/workflows/tasks/{taskId}/complete"));

        assertThat(collectEndpoints()).containsAll(expected);
    }

    /**
     * 验证业务操作接口全部正确注册。
     */
    @Test
    @DisplayName("业务操作接口全部正确注册")
    void shouldExposeWorkflowOperationEndpoints() {
        Set<Endpoint> expected = Set.of(
                new Endpoint("POST", "/api/workflow/tasks/{taskId}/rollback"),
                new Endpoint("POST", "/api/workflow/tasks/{taskId}/transfer"),
                new Endpoint("POST", "/api/workflow/tasks/{taskId}/delegate"),
                new Endpoint("POST", "/api/workflow/instances/{instanceId}/suspend"),
                new Endpoint("POST", "/api/workflow/instances/{instanceId}/resume"));

        assertThat(collectEndpoints()).containsAll(expected);
    }

    /**
     * 验证对外暴露的端点集合与预期完全一致（数量、路径、方法一一对应）。
     */
    @Test
    @DisplayName("对外暴露端点集合与预期精确一致")
    void shouldExposeExactlyExpectedEndpoints() {
        Set<Endpoint> expected = new LinkedHashSet<>();
        expected.add(new Endpoint("GET", "/admin-api/v1/workflows/instances"));
        expected.add(new Endpoint("GET", "/admin-api/v1/workflows/instances/{instanceId}"));
        expected.add(new Endpoint("GET", "/admin-api/v1/workflows/statistics"));
        expected.add(new Endpoint("GET", "/admin-api/v1/workflows/statistics/trend"));
        expected.add(new Endpoint("POST", "/api/v1/workflows/{workflowCode}/instances"));
        expected.add(new Endpoint("GET", "/api/v1/workflows/tasks/pending"));
        expected.add(new Endpoint("POST", "/api/v1/workflows/tasks/{taskId}/complete"));
        expected.add(new Endpoint("POST", "/api/workflow/tasks/{taskId}/rollback"));
        expected.add(new Endpoint("POST", "/api/workflow/tasks/{taskId}/transfer"));
        expected.add(new Endpoint("POST", "/api/workflow/tasks/{taskId}/delegate"));
        expected.add(new Endpoint("POST", "/api/workflow/instances/{instanceId}/suspend"));
        expected.add(new Endpoint("POST", "/api/workflow/instances/{instanceId}/resume"));

        Set<Endpoint> actual = collectEndpoints();

        assertThat(actual).as("工作流对外接口总数").hasSize(12);
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    /**
     * 收集全部路由 Bean 暴露的端点集合。
     *
     * <p>反射调用 {@link WorkflowRouter} 的三个路由工厂方法，路由 Bean 依赖的
     * {@link WorkflowHandler} 使用 Mockito 桩对象填充，仅用于构建路由树，
     * 不触发任何业务逻辑。</p>
     *
     * @return 已注册的「HTTP 方法 + 绝对路径」端点集合
     */
    private Set<Endpoint> collectEndpoints() {
        WorkflowRouter router = new WorkflowRouter();
        WorkflowHandler handler = Mockito.mock(WorkflowHandler.class);
        Set<Endpoint> endpoints = new LinkedHashSet<>();
        for (String factoryName : ROUTER_FACTORIES) {
            RouterFunction<ServerResponse> routes = invokeFactory(router, handler, factoryName);
            RouteCollector collector = new RouteCollector();
            routes.accept(collector);
            endpoints.addAll(collector.endpoints);
        }
        return endpoints;
    }

    /**
     * 反射调用路由工厂方法。
     *
     * @param router      路由 Bean 实例
     * @param handler     处理器桩对象
     * @param factoryName 工厂方法名称
     * @return 构建出的路由函数
     */
    @SuppressWarnings("unchecked")
    private RouterFunction<ServerResponse> invokeFactory(
            WorkflowRouter router, WorkflowHandler handler, String factoryName) {
        try {
            return (RouterFunction<ServerResponse>) WorkflowRouter.class
                    .getMethod(factoryName, WorkflowHandler.class)
                    .invoke(router, handler);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法构建路由 Bean: " + factoryName, e);
        }
    }

    /** HTTP 方法与绝对路径组成的接口身份。 */
    private record Endpoint(String method, String path) {}

    /** 从 Spring RouterFunction 访问器回调中收集真实端点。 */
    private static final class RouteCollector implements RouterFunctions.Visitor {

        /** 当前嵌套路由路径栈。 */
        private final ArrayDeque<String> nesting = new ArrayDeque<>();

        /** 已识别的业务端点。 */
        private final Set<Endpoint> endpoints = new LinkedHashSet<>();

        /** {@inheritDoc} */
        @Override
        public void startNested(RequestPredicate predicate) {
            nesting.addLast(predicate.toString());
        }

        /** {@inheritDoc} */
        @Override
        public void endNested(RequestPredicate predicate) {
            nesting.removeLast();
        }

        /** {@inheritDoc} */
        @Override
        public void route(RequestPredicate predicate, HandlerFunction<?> handlerFunction) {
            String predicateText = predicate.toString();
            Matcher method = METHOD_PATTERN.matcher(predicateText);
            Matcher path = PATH_PATTERN.matcher(predicateText);
            assertThat(method.find()).as(predicateText).isTrue();
            String relativePath = path.find() ? path.group(1) : extractTopLevelPath(predicateText);
            String absolutePath = String.join("", nesting) + relativePath;
            endpoints.add(new Endpoint(method.group(1), absolutePath));
        }

        /**
         * 从顶层非嵌套路由谓词（如 {@code "GET && /"}）中提取路径。
         *
         * @param predicateText 路由谓词字符串
         * @return 路径部分；无法识别时返回空串
         */
        private static String extractTopLevelPath(String predicateText) {
            int andIndex = predicateText.indexOf("&&");
            if (andIndex < 0) {
                return "";
            }
            String remainder = predicateText.substring(andIndex + 2).trim();
            if (!remainder.startsWith("/")) {
                return "";
            }
            int closeParen = remainder.indexOf(')');
            return closeParen >= 0 ? remainder.substring(0, closeParen) : remainder;
        }

        /** {@inheritDoc} */
        @Override
        public void resources(Function<ServerRequest, Mono<Resource>> lookupFunction) {}

        /** {@inheritDoc} */
        @Override
        public void attributes(Map<String, Object> attributes) {}

        /** {@inheritDoc} */
        @Override
        public void unknown(RouterFunction<?> routerFunction) {}
    }
}
