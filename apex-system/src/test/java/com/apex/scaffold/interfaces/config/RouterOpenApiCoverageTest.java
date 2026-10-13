package com.apex.scaffold.interfaces.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.interfaces.router.CaptchaRouter;
import com.apex.scaffold.interfaces.router.ConfigRouter;
import com.apex.scaffold.interfaces.router.DatabaseConfigRouter;
import com.apex.scaffold.interfaces.router.ExportRouter;
import com.apex.scaffold.interfaces.router.JobRouter;
import com.apex.scaffold.interfaces.router.LogRouter;
import com.apex.scaffold.interfaces.router.MonitorRouter;
import com.apex.scaffold.interfaces.router.NotificationRouter;
import com.apex.scaffold.interfaces.router.PublicApiRouter;
import com.apex.scaffold.interfaces.router.RbacRouter;
import com.apex.scaffold.interfaces.router.SearchRouter;
import com.apex.scaffold.interfaces.router.SystemMessageRouter;
import com.apex.scaffold.interfaces.router.UserAdminRouter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

/**
 * 函数式路由与 OpenAPI 声明一致性测试。
 *
 * <p>直接访问各子域路由器构建出的路由树，并与 {@link RouterOpenApiConfig} 上的
 * {@link RouterOperation} 声明比较，防止新增接口漏写文档或文档继续保留已删除的接口。
 *
 * @author Apex Team
 */
class RouterOpenApiCoverageTest {

    /** HTTP 方法与路径提取表达式。 */
    private static final Pattern METHOD_PATTERN = Pattern.compile("\\b(GET|POST|PUT|DELETE|PATCH)\\b");

    /** 路由谓词中的相对路径提取表达式。 */
    private static final Pattern PATH_PATTERN = Pattern.compile("&& (/[^)]+)\\)");

    /** 采用 202 表示异步任务已受理的操作。 */
    private static final Set<String> ASYNC_ACCEPTED_OPERATION_IDS = Set.of("deleteCurrentUser");

    /**
     * 验证所有真实业务路由均有且只有一个 OpenAPI 声明。
     *
     * @throws Exception 当反射创建路由失败时
     */
    @Test
    void shouldKeepRouterAndOpenApiEndpointsInSync() throws Exception {
        Set<Endpoint> actualRoutes = actualRoutes();
        // 排除内部代理路由（/tos/**, /minio/**）：它们不是业务 API，不需要 OpenAPI 文档
        Set<Endpoint> filteredActualRoutes = actualRoutes.stream()
                .filter(e -> !e.path().startsWith("/tos") && !e.path().startsWith("/minio"))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<Endpoint> documentedRoutes = documentedOperations().stream()
                .flatMap(operation ->
                        Arrays.stream(operation.method()).map(method -> new Endpoint(method.name(), operation.path())))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        assertThat(documentedRoutes).containsExactlyInAnyOrderElementsOf(filteredActualRoutes);
        assertThat(documentedOperations()).hasSameSizeAs(documentedRoutes);
    }

    /**
     * 验证每个文档操作都有唯一标识、摘要以及可解析的 Handler 方法和标签。
     *
     * @throws Exception 当 Handler 方法反射解析失败时
     */
    @Test
    void shouldExposeCompleteHandlerMetadata() throws Exception {
        List<RouterOperation> operations = documentedOperations();
        Set<String> operationIds = new HashSet<>();

        for (RouterOperation route : operations) {
            Operation operation = route.operation();
            assertThat(operation.operationId()).as(route.path()).isNotBlank();
            assertThat(operation.summary()).as(route.path()).isNotBlank();
            assertThat(operationIds.add(operation.operationId()))
                    .as(operation.operationId())
                    .isTrue();

            Method handlerMethod = route.beanClass().getMethod(route.beanMethod(), ServerRequest.class);
            assertThat(handlerMethod.getAnnotation(Operation.class))
                    .as(route.beanMethod())
                    .isNotNull();
            assertThat(route.beanClass().getAnnotation(Tag.class))
                    .as(route.beanClass().getName())
                    .isNotNull();
        }
    }

    /**
     * 验证 Router 声明与 Handler 方法使用同一个 operationId。
     *
     * @throws Exception 当 Handler 方法反射解析失败时
     */
    @Test
    void shouldKeepRouterAndHandlerOperationIdsInSync() throws Exception {
        for (RouterOperation route : documentedOperations()) {
            Method handlerMethod = route.beanClass().getMethod(route.beanMethod(), ServerRequest.class);
            Operation handlerOperation = handlerMethod.getAnnotation(Operation.class);
            assertThat(handlerOperation)
                    .as(route.path() + " " + route.beanMethod())
                    .isNotNull();
            assertThat(handlerOperation.operationId())
                    .as(route.path() + " " + route.beanMethod())
                    .isEqualTo(route.operation().operationId());
        }
    }

    /**
     * 验证 OpenAPI Customizer 按 operationId 维护的契约映射没有孤儿键。
     *
     * @throws Exception 当反射读取配置字段失败时
     */
    @Test
    void shouldKeepCustomizerOperationMapsFreeOfOrphans() throws Exception {
        Set<String> operationIds = documentedOperations().stream()
                .map(route -> route.operation().operationId())
                .collect(java.util.stream.Collectors.toSet());

        for (Field field : OpenApiDocumentationCustomizer.class.getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(null);
            assertThat(value).as(field.getName()).isInstanceOf(Map.class);
            Map<?, ?> mapping = (Map<?, ?>) value;
            assertThat(mapping.keySet())
                    .as(field.getName())
                    .allMatch(key -> key instanceof String && operationIds.contains(key));
        }
    }

    /**
     * 验证定制后请求体媒体类型与 Customizer 契约映射一致。
     *
     * <p>JSON 请求体操作应为 application/json，表单操作应为 application/x-www-form-urlencoded，
     * GET/DELETE 等无请求体操作不应声明 requestBody。</p>
     */
    @Test
    void shouldApplyCorrectRequestMediaTypesAfterCustomization() {
        OpenAPI openApi = customizedOpenApi();
        Set<String> jsonOps = operationIdsFromMap("JSON_REQUEST_TYPES");
        Set<String> declaredBodyOps = new HashSet<>();
        declaredBodyOps.addAll(jsonOps);
        declaredBodyOps.add("validateMobile");
        declaredBodyOps.add("deleteCurrentUser");

        for (RouterOperation route : documentedOperations()) {
            String opId = route.operation().operationId();
            io.swagger.v3.oas.models.Operation operation = findOperation(openApi, opId);
            boolean isGetOrDelete = route.method()[0] == RequestMethod.GET || route.method()[0] == RequestMethod.DELETE;
            if (isGetOrDelete && !declaredBodyOps.contains(opId)) {
                assertThat(operation.getRequestBody())
                        .as(opId + " GET/DELETE 不应有请求体")
                        .isNull();
                continue;
            }
            if (!declaredBodyOps.contains(opId)) {
                continue;
            }
            assertThat(operation.getRequestBody()).as(opId + " 应声明请求体").isNotNull();
            assertThat(operation.getRequestBody().getContent())
                    .as(opId + " 请求体应声明内容")
                    .isNotNull();
            if ("validateMobile".equals(opId)) {
                assertThat(operation.getRequestBody().getContent().keySet())
                        .as(opId + " 应为 application/x-www-form-urlencoded")
                        .contains("application/x-www-form-urlencoded");
            } else {
                assertThat(operation.getRequestBody().getContent().keySet())
                        .as(opId + " 应为 application/json")
                        .contains("application/json");
            }
        }
    }

    /**
     * 验证定制后响应媒体类型符合接口语义。
     *
     * <p>SSE 操作为 text/event-stream，其余操作的成功响应为 application/json。
     * 文件下载能力已随文件域迁出至 apex-file-service，此处不再断言二进制响应。</p>
     */
    @Test
    void shouldApplyCorrectResponseMediaTypesAfterCustomization() {
        OpenAPI openApi = customizedOpenApi();
        Set<String> sseOps = Set.of("streamNotifications");

        for (RouterOperation route : documentedOperations()) {
            String opId = route.operation().operationId();
            io.swagger.v3.oas.models.Operation operation = findOperation(openApi, opId);
            String successCode = "deleteCurrentUser".equals(opId) ? "202" : "200";
            assertThat(operation.getResponses()).as(opId + " 应有成功响应").containsKey(successCode);
            var content = operation.getResponses().get(successCode).getContent();
            assertThat(content).as(opId + " 成功响应应有内容").isNotNull();
            if (sseOps.contains(opId)) {
                assertThat(content.keySet())
                        .as(opId + " SSE 应为 text/event-stream")
                        .contains("text/event-stream");
            } else {
                assertThat(content.keySet())
                        .as(opId + " 普通接口应为 application/json")
                        .contains("application/json");
            }
        }
    }

    /**
     * 验证 Customizer 引用的所有请求/响应 DTO 类都具有类级 @Schema 注解。
     *
     * @throws Exception 当反射读取字段失败时
     */
    @Test
    void shouldExposeSchemaAnnotationsOnReferencedDtos() throws Exception {
        for (String fieldName : List.of("JSON_REQUEST_TYPES", "RESPONSE_TYPES")) {
            Field field = OpenApiDocumentationCustomizer.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Map<?, ?> mapping = (Map<?, ?>) field.get(null);
            for (Object value : mapping.values()) {
                assertThat(value).as(fieldName).isInstanceOf(Class.class);
                Class<?> dtoClass = (Class<?>) value;
                assertThat(dtoClass.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class))
                        .as(fieldName + " -> " + dtoClass.getName())
                        .isNotNull();
            }
        }
    }

    /**
     * 验证声明了查询参数的操作在定制后参数确实出现在 OpenAPI 操作中。
     *
     * @throws Exception 当反射读取字段失败时
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldDeclareQueryParametersForDocumentedEndpoints() throws Exception {
        Field field = OpenApiDocumentationCustomizer.class.getDeclaredField("QUERY_PARAMETERS");
        field.setAccessible(true);
        Map<String, List<?>> queryParams = (Map<String, List<?>>) field.get(null);

        OpenAPI openApi = customizedOpenApi();
        for (Map.Entry<String, List<?>> entry : queryParams.entrySet()) {
            String opId = entry.getKey();
            io.swagger.v3.oas.models.Operation operation = findOperation(openApi, opId);
            assertThat(operation).as(opId + " 应存在于文档中").isNotNull();
            assertThat(operation.getParameters()).as(opId + " 应声明查询参数").isNotNull();
            for (Object param : entry.getValue()) {
                String name = (String) param.getClass().getMethod("name").invoke(param);
                assertThat(operation.getParameters())
                        .as(opId + " 应声明查询参数 " + name)
                        .anyMatch(p -> name.equals(p.getName()) && "query".equals(p.getIn()));
            }
        }
    }

    /**
     * 验证生成文档正确区分匿名接口和需要 JWT 的业务接口。
     */
    @Test
    void shouldDocumentBearerAuthenticationAccordingToSecurityWhitelist() {
        SpringDocConfig config = new SpringDocConfig();
        OpenAPI openApi = config.apexOpenApi();
        openApi.setPaths(new Paths()
                .addPathItem(
                        "/api/v1/auth/login",
                        new PathItem().post(new io.swagger.v3.oas.models.Operation().responses(new ApiResponses())))
                .addPathItem(
                        "/api/v1/profile/me",
                        new PathItem().get(new io.swagger.v3.oas.models.Operation().responses(new ApiResponses()))));

        config.openApiCustomizer().customise(openApi);

        assertThat(openApi.getComponents().getSecuritySchemes()).containsKey("BearerAuth");
        assertThat(openApi.getSecurity()).isNotEmpty();
        assertThat(openApi.getPaths().get("/api/v1/auth/login").getPost().getSecurity())
                .isEmpty();
        assertThat(openApi.getPaths().get("/api/v1/profile/me").getGet().getResponses())
                .containsKeys("401", "403");
    }

    /** 验证系统管理文档组与后台用户管理文档组分别覆盖正确的 URL 前缀。 */
    @Test
    void shouldExposeDedicatedAdminOpenApiGroups() {
        SpringDocConfig config = new SpringDocConfig();
        var customizer = config.openApiCustomizer();

        var systemGroup = config.adminSystemApi(customizer);
        assertThat(systemGroup.getGroup()).isEqualTo("admin-system");
        assertThat(systemGroup.getPathsToMatch())
                .contains(
                        "/admin-api/v1/menus/**",
                        "/admin-api/v1/dict/**",
                        "/admin-api/v1/configs/**",
                        "/admin-api/v1/jobs/**",
                        "/admin-api/v1/login-logs/**",
                        "/api/v1/auth/captcha/**");

        var userGroup = config.adminUserApi(customizer);
        assertThat(userGroup.getGroup()).isEqualTo("admin-user");
        assertThat(userGroup.getPathsToMatch())
                .containsExactly("/admin-api/v1/users/**", "/admin-api/v1/staff-roles/**");
    }

    /**
     * 验证全部函数式路由经过增强后都具有可读说明、成功响应媒体类型和标准错误结构。
     */
    @Test
    void shouldEnrichEveryDocumentedOperationWithCompleteResponseMetadata() {
        OpenAPI openApi = new SpringDocConfig().apexOpenApi();
        Paths paths = new Paths();
        for (RouterOperation route : documentedOperations()) {
            PathItem pathItem = paths.computeIfAbsent(route.path(), ignored -> new PathItem());
            io.swagger.v3.oas.models.Operation operation = new io.swagger.v3.oas.models.Operation()
                    .operationId(route.operation().operationId())
                    .summary(route.operation().summary())
                    .responses(new ApiResponses());
            for (RequestMethod method : route.method()) {
                pathItem.operation(PathItem.HttpMethod.valueOf(method.name()), operation);
            }
        }
        openApi.setPaths(paths);

        new SpringDocConfig().openApiCustomizer().customise(openApi);

        openApi.getPaths().forEach((path, pathItem) -> pathItem.readOperations().forEach(operation -> {
            assertThat(operation.getDescription()).as(path).isNotBlank();
            String successCode = ASYNC_ACCEPTED_OPERATION_IDS.contains(operation.getOperationId()) ? "202" : "200";
            assertThat(operation.getResponses()).as(path).containsKeys(successCode, "400", "429", "500");
            assertThat(operation.getResponses().get(successCode).getContent())
                    .as(path)
                    .isNotEmpty();
            for (String status : List.of("400", "429", "500")) {
                assertThat(operation.getResponses().get(status).getContent())
                        .as(path + " " + status)
                        .containsKey("application/json");
            }
            Matcher variables = Pattern.compile("\\{([^}/]+)}").matcher(path);
            while (variables.find()) {
                String name = variables.group(1);
                assertThat(operation.getParameters())
                        .as(path + " path parameter " + name)
                        .anyMatch(parameter -> name.equals(parameter.getName())
                                && "path".equals(parameter.getIn())
                                && Boolean.TRUE.equals(parameter.getRequired()));
            }
        }));
    }

    /**
     * 构建真实函数式路由并提取端点集合。
     *
     * <p>覆盖全部业务路由工厂：通用 /api/v1 模块（apiRoutes）、后台用户管理
     * （adminUserManagementRoutes）以及 P0 系统管理模块（菜单/字典/参数/定时任务/登录日志/验证码）。
     * 前端静态壳与短入口重定向路由已随前后端分离迁移移除，不纳入覆盖范围。</p>
     *
     * @return 已注册的 HTTP 方法和绝对路径集合
     * @throws Exception 当路由 Bean 反射调用失败时
     */
    private Set<Endpoint> actualRoutes() throws Exception {
        List<?> routers = List.of(
                new PublicApiRouter(),
                new UserAdminRouter(),
                new RbacRouter(),
                new ConfigRouter(),
                new JobRouter(),
                new LogRouter(),
                new MonitorRouter(),
                new CaptchaRouter(),
                new ExportRouter(),
                new NotificationRouter(),
                new SystemMessageRouter(),
                new DatabaseConfigRouter(),
                new SearchRouter());
        Set<Endpoint> endpoints = new LinkedHashSet<>();
        for (String factoryName : List.of(
                "apiRoutes",
                "adminUserManagementRoutes",
                "adminMenuRoutes",
                "adminDictRoutes",
                "adminJobRoutes",
                "adminConfigRoutes",
                "adminLoginLogRoutes",
                "adminAuditLogRoutes",
                "adminApiCallLogRoutes",
                "adminOnlineUserRoutes",
                "adminDeptRoutes",
                "adminExportRoutes",
                "notificationSseRoutes",
                "systemMessageRoutes",
                "adminMonitorRoutes",
                "captchaRoutes",
                "unifiedSearchRoutes")) {
            for (Object router : routers) {
                Method factory = Arrays.stream(router.getClass().getMethods())
                        .filter(method -> method.getName().equals(factoryName))
                        .findFirst()
                        .orElse(null);
                if (factory == null) {
                    continue;
                }
                Object[] handlers = Arrays.stream(factory.getParameterTypes())
                        .map(Mockito::mock)
                        .toArray();
                RouterFunction<?> routes = (RouterFunction<?>) factory.invoke(router, handlers);
                RouteCollector collector = new RouteCollector();
                routes.accept(collector);
                endpoints.addAll(collector.endpoints);
            }
        }
        return endpoints;
    }

    /**
     * 读取配置类中全部函数式端点文档声明。
     *
     * @return OpenAPI 路由声明列表
     */
    private List<RouterOperation> documentedOperations() {
        List<RouterOperation> operations = new ArrayList<>();
        for (Method method : RouterOpenApiConfig.class.getDeclaredMethods()) {
            RouterOperations annotations = method.getAnnotation(RouterOperations.class);
            if (annotations != null) {
                operations.addAll(List.of(annotations.value()));
            }
        }
        return operations;
    }

    /**
     * 构建包含全部文档操作并经过 Customizer 增强的 OpenAPI 实例。
     *
     * @return 已定制的 OpenAPI 文档
     */
    private OpenAPI customizedOpenApi() {
        OpenAPI openApi = new SpringDocConfig().apexOpenApi();
        Paths paths = new Paths();
        for (RouterOperation route : documentedOperations()) {
            PathItem pathItem = paths.computeIfAbsent(route.path(), ignored -> new PathItem());
            io.swagger.v3.oas.models.Operation operation = new io.swagger.v3.oas.models.Operation()
                    .operationId(route.operation().operationId())
                    .summary(route.operation().summary())
                    .responses(new ApiResponses());
            for (RequestMethod method : route.method()) {
                pathItem.operation(PathItem.HttpMethod.valueOf(method.name()), operation);
            }
        }
        openApi.setPaths(paths);
        new SpringDocConfig().openApiCustomizer().customise(openApi);
        return openApi;
    }

    /**
     * 按 operationId 在定制后的 OpenAPI 中查找操作。
     *
     * @param openApi 已定制的 OpenAPI 文档
     * @param operationId 操作唯一标识
     * @return 匹配的 OpenAPI 操作；未找到时返回 {@code null}
     */
    private io.swagger.v3.oas.models.Operation findOperation(OpenAPI openApi, String operationId) {
        return openApi.getPaths().values().stream()
                .flatMap(pathItem -> pathItem.readOperations().stream())
                .filter(operation -> operationId.equals(operation.getOperationId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 反射读取 Customizer 指定 Map 字段的 key 集合。
     *
     * @param fieldName 字段名
     * @return operationId 集合
     */
    @SuppressWarnings("unchecked")
    private Set<String> operationIdsFromMap(String fieldName) {
        try {
            Field field = OpenApiDocumentationCustomizer.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Map<String, ?> map = (Map<String, ?>) field.get(null);
            return map.keySet();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法读取字段 " + fieldName, e);
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
         * 从顶层非嵌套路由谓词（如 {@code "GET && /"}、{@code "GET && /tos/**"}）中提取路径。
         *
         * <p>嵌套路由（如 {@code "(GET && /api/v1)"}）由 {@link #PATH_PATTERN} 处理；
         * 顶层路由的谓词没有外层括号，需在此兜底提取。</p>
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
