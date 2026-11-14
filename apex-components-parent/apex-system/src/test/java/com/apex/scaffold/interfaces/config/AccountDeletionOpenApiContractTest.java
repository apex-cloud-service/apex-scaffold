package com.apex.scaffold.interfaces.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * 账号注销 OpenAPI 契约测试。
 *
 * <p>项目当前使用 SpringDoc 函数式路由声明作为 REST Docs 等价物；本测试从
 * {@link RouterOpenApiConfig} 的路由元数据构造 OpenAPI 操作，再运行真实的文档增强器，
 * 因此不会依赖注销业务、认证实现或数据库状态。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class AccountDeletionOpenApiContractTest {

    private static final String DELETION_CODE_PATH = "/api/v1/user/me/deletion-code";
    private static final String DELETE_USER_PATH = "/api/v1/user/me";

    /**
     * 验证注销端点声明的路径、方法、处理器绑定与操作说明。
     */
    @Test
    void shouldDeclareAccountDeletionEndpointsAndBindings() throws Exception {
        List<RouterOperation> routes = deletionRoutes();

        assertThat(routes).hasSize(2);
        assertThat(routes)
                .extracting(route -> route.path())
                .containsExactlyInAnyOrder(DELETION_CODE_PATH, DELETE_USER_PATH);

        RouterOperation codeRoute = route(routes, DELETION_CODE_PATH, RequestMethod.POST);
        RouterOperation deleteRoute = route(routes, DELETE_USER_PATH, RequestMethod.DELETE);

        assertThat(codeRoute.beanClass()).isEqualTo(com.apex.scaffold.interfaces.handler.UserHandler.class);
        assertThat(codeRoute.beanMethod()).isEqualTo("sendAccountDeletionCode");
        assertThat(deleteRoute.beanClass()).isEqualTo(com.apex.scaffold.interfaces.handler.UserHandler.class);
        assertThat(deleteRoute.beanMethod()).isEqualTo("deleteCurrentUser");

        assertThat(codeRoute.operation().description()).contains("手机号", "不接受");
        assertThat(deleteRoute.operation().description()).contains("失效账号", "异步清理");
    }

    /**
     * 验证注销请求体、202 受理响应、示例及统一错误响应经过自定义器增强后
     * 可被客户端生成器消费。
     */
    @Test
    void shouldEnrichAccountDeletionSchemasExamplesAndResponses() throws Exception {
        List<RouterOperation> routes = deletionRoutes();
        OpenAPI openApi = new SpringDocConfig().apexOpenApi();
        openApi.setPaths(toOpenApiPaths(routes));

        new SpringDocConfig().openApiCustomizer().customise(openApi);

        io.swagger.v3.oas.models.Operation code =
                openApi.getPaths().get(DELETION_CODE_PATH).getPost();
        io.swagger.v3.oas.models.Operation delete =
                openApi.getPaths().get(DELETE_USER_PATH).getDelete();

        assertThat(code.getRequestBody()).as("手机号只能从账号读取，发送验证码接口不接收手机号请求体").isNull();
        assertThat(code.getResponses()).containsKeys("200", "400", "401", "403", "429", "500");
        assertThat(code.getResponses()
                        .get("200")
                        .getContent()
                        .get("application/json")
                        .getExamples())
                .containsOnlyKeys("sent");

        RequestBody requestBody = delete.getRequestBody();
        assertThat(requestBody).isNotNull();
        Schema<?> requestSchema =
                requestBody.getContent().get("application/json").getSchema();
        // 请求体 schema 可能是 $ref 引用，解引用到组件模型后校验字段契约。
        if (requestSchema.get$ref() != null) {
            String componentName =
                    requestSchema.get$ref().substring(requestSchema.get$ref().lastIndexOf('/') + 1);
            requestSchema = openApi.getComponents().getSchemas().get(componentName);
        }
        assertThat(requestSchema.getRequired()).containsExactly("code");
        Schema<?> codeSchema = (Schema<?>) requestSchema.getProperties().get("code");
        assertThat(codeSchema.getType()).isEqualTo("string");
        assertThat(codeSchema.getPattern()).isEqualTo("\\d{6}");
        assertThat(codeSchema.getExample()).isEqualTo("123456");

        assertThat(delete.getResponses()).containsKeys("202", "400", "401", "403", "409", "429", "500");
        assertThat(delete.getResponses()).doesNotContainKey("200");
        Schema<?> responseEnvelope = delete.getResponses()
                .get("202")
                .getContent()
                .get("application/json")
                .getSchema();
        Schema<?> acceptedData = (Schema<?>) responseEnvelope.getProperties().get("data");
        assertThat(acceptedData.getProperties()).containsKeys("requestId", "jobId", "status");
        assertThat(delete.getResponses()
                        .get("202")
                        .getContent()
                        .get("application/json")
                        .getExamples())
                .containsOnlyKeys("accepted");

        for (String status : List.of("400", "401", "403", "409", "429", "500")) {
            assertThat(delete.getResponses().get(status).getContent())
                    .as("DELETE /api/v1/user/me %s", status)
                    .containsKey("application/json");
        }
    }

    /**
     * 反射读取 {@link RouterOpenApiConfig} 上 {@code userRoutesDocs} 方法的
     * {@code RouterOperations} 注解，返回注销两个端点的路由声明列表。
     *
     * @return 注销相关路由声明
     * @throws Exception 反射或注解读取失败时
     */
    private List<RouterOperation> deletionRoutes() throws Exception {
        Method docsMethod = RouterOpenApiConfig.class.getDeclaredMethod("userRoutesDocs");
        RouterOperations operations = docsMethod.getAnnotation(RouterOperations.class);
        assertThat(operations).isNotNull();
        return Arrays.stream(operations.value())
                .filter(route -> DELETION_CODE_PATH.equals(route.path()) || DELETE_USER_PATH.equals(route.path()))
                .toList();
    }

    /**
     * 按路径与 HTTP 方法从路由声明列表中查找匹配的路由。
     *
     * @param routes 候选路由声明
     * @param path 目标路径
     * @param method 目标 HTTP 方法
     * @return 匹配的路由声明；不存在时抛出异常
     */
    private RouterOperation route(List<RouterOperation> routes, String path, RequestMethod method) {
        return routes.stream()
                .filter(candidate -> path.equals(candidate.path())
                        && Arrays.asList(candidate.method()).contains(method))
                .findFirst()
                .orElseThrow();
    }

    /**
     * 将路由声明转换为可按路径与 HTTP 方法读取的 OpenAPI Paths 结构，
     * 仅保留操作标识、摘要、说明与响应码，供文档增强器消费。
     *
     * @param routes 路由声明列表
     * @return 转换后的 OpenAPI 路径集合
     */
    private Paths toOpenApiPaths(List<RouterOperation> routes) {
        Paths paths = new Paths();
        for (RouterOperation route : routes) {
            io.swagger.v3.oas.annotations.Operation source = route.operation();
            io.swagger.v3.oas.models.Operation operation = new io.swagger.v3.oas.models.Operation()
                    .operationId(source.operationId())
                    .summary(source.summary())
                    .description(source.description())
                    .responses(new ApiResponses());
            Arrays.stream(source.responses()).forEach(response -> operation
                    .getResponses()
                    .addApiResponse(response.responseCode(), new ApiResponse().description(response.description())));
            PathItem pathItem = paths.computeIfAbsent(route.path(), ignored -> new PathItem());
            Arrays.stream(route.method())
                    .forEach(method -> pathItem.operation(PathItem.HttpMethod.valueOf(method.name()), operation));
        }
        return paths;
    }
}
