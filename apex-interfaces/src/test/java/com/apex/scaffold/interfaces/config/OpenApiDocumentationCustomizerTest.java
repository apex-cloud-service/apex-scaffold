package com.apex.scaffold.interfaces.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * {@link OpenApiDocumentationCustomizer} 文档完整性测试。
 *
 * @author Apex Team
 */
class OpenApiDocumentationCustomizerTest {

    /**
     * 验证 JSON DTO、统一响应模型和公开接口安全信息。
     */
    @Test
    void shouldDocumentJsonRequestAndUnifiedResponse() {
        io.swagger.v3.oas.models.Operation login = operation("authLogin");
        login.setResponses(new ApiResponses()
                .addApiResponse(
                        "200",
                        new ApiResponse()
                                .description("登录成功")
                                .content(new Content()
                                        .addMediaType(
                                                "application/json",
                                                new MediaType()
                                                        .schema(new Schema<>()
                                                                .$ref("#/components/schemas/LoginResponse"))))));
        OpenAPI openApi = openApi("/api/v1/auth/login", new PathItem().post(login));

        new SpringDocConfig().openApiCustomizer().customise(openApi);

        assertThat(login.getRequestBody()).isNotNull();
        assertThat(login.getRequestBody().getContent()).containsKey("application/json");
        assertThat(login.getRequestBody()
                        .getContent()
                        .get("application/json")
                        .getSchema()
                        .get$ref())
                .endsWith("/LoginRequest");
        Schema<?> response = login.getResponses()
                .get("200")
                .getContent()
                .get("application/json")
                .getSchema();
        assertThat(response.getProperties()).containsKeys("code", "msg", "data", "traceId");
        assertThat(((Schema<?>) response.getProperties().get("data")).get$ref()).endsWith("/LoginResponse");
        assertThat(login.getResponses()).containsKeys("200", "400", "429", "500");
        assertThat(login.getResponses()).doesNotContainKeys("401", "403");
        assertThat(login.getSecurity()).isEmpty();
        assertThat(login.getSummary()).isEqualTo("用户登录");
        assertThat(login.getDescription())
                .contains("REGISTER_REQUIRED", "registrationTicket", "PHONE_BIND_REQUIRED", "wechatTicket");
        assertThat(login.getResponses()
                        .get("200")
                        .getContent()
                        .get("application/json")
                        .getExamples())
                .containsOnlyKeys("authenticated", "registerRequired", "phoneBindRequired");
    }

    /**
     * 验证裸 Result 注解会替换为带字段说明和示例的统一响应模型。
     */
    @Test
    void shouldReplaceGenericResultWithDocumentedEnvelope() {
        io.swagger.v3.oas.models.Operation operation = operation("healthCheck");
        operation.setResponses(new ApiResponses()
                .addApiResponse(
                        "200",
                        new ApiResponse()
                                .description("成功")
                                .content(new Content()
                                        .addMediaType(
                                                "application/json",
                                                new MediaType()
                                                        .schema(new Schema<>().$ref("#/components/schemas/Result")))))
                .addApiResponse(
                        "400",
                        new ApiResponse()
                                .description("失败")
                                .content(new Content()
                                        .addMediaType(
                                                "application/json",
                                                new MediaType()
                                                        .schema(new Schema<>().$ref("#/components/schemas/Result"))))));
        OpenAPI openApi = openApi("/api/v1/health", new PathItem().get(operation));

        new SpringDocConfig().openApiCustomizer().customise(openApi);

        assertThat(operation
                        .getResponses()
                        .get("200")
                        .getContent()
                        .get("application/json")
                        .getSchema()
                        .get$ref())
                .endsWith("/ApiSuccessResponse");
        assertThat(operation
                        .getResponses()
                        .get("400")
                        .getContent()
                        .get("application/json")
                        .getSchema()
                        .get$ref())
                .endsWith("/ApiErrorResponse");
    }

    /**
     * 验证路径参数、查询参数、认证错误响应和响应媒体类型。
     */
    @Test
    void shouldDocumentPathAndQueryParametersForProtectedEndpoint() {
        io.swagger.v3.oas.models.Operation detail = operation("adminGetUser");
        detail.addParametersItem(new Parameter().name("userId").in("path").schema(new StringSchema()));
        io.swagger.v3.oas.models.Operation list = operation("adminListUsers");
        OpenAPI openApi = new SpringDocConfig().apexOpenApi();
        openApi.setPaths(new Paths()
                .addPathItem("/admin-api/v1/users/{userId}", new PathItem().get(detail))
                .addPathItem("/admin-api/v1/users", new PathItem().get(list)));

        new SpringDocConfig().openApiCustomizer().customise(openApi);

        Parameter userId = detail.getParameters().stream()
                .filter(parameter -> parameter.getName().equals("userId"))
                .findFirst()
                .orElseThrow();
        assertThat(userId.getSchema().getFormat()).isEqualTo("int64");
        assertThat(userId.getRequired()).isTrue();
        Set<String> listParameters = list.getParameters().stream()
                .map(parameter -> parameter.getIn() + ":" + parameter.getName())
                .collect(Collectors.toSet());
        assertThat(listParameters)
                .containsExactlyInAnyOrder("query:page", "query:size", "query:keyword", "query:status");
        assertThat(list.getResponses()).containsKeys("200", "400", "401", "403", "429", "500");
        assertThat(list.getResponses().get("200").getContent()).containsKey("application/json");
        assertThat(detail.getDescription()).contains("traceId");
    }

    /**
     * 验证 Handler 读取的幂等请求头会出现在 Swagger UI 参数区。
     */
    @Test
    void shouldDocumentIdempotencyHeader() {
        io.swagger.v3.oas.models.Operation delete = operation("deleteCurrentUser");
        OpenAPI openApi = openApi("/api/v1/user/me", new PathItem().delete(delete));

        new SpringDocConfig().openApiCustomizer().customise(openApi);

        assertThat(delete.getParameters())
                .anyMatch(parameter -> "Idempotency-Key".equals(parameter.getName())
                        && "header".equals(parameter.getIn())
                        && !Boolean.TRUE.equals(parameter.getRequired()));
    }

    /**
     * 验证表单请求和二进制下载的 Content-Type。
     */
    @Test
    void shouldDocumentFormRequestAndBinaryResponse() {
        io.swagger.v3.oas.models.Operation validation = operation("validateMobile");
        io.swagger.v3.oas.models.Operation download = operation("getLocalFileContent");
        OpenAPI openApi = new SpringDocConfig().apexOpenApi();
        openApi.setPaths(new Paths()
                .addPathItem("/api/v1/auth/cl/validate-mobile", new PathItem().post(validation))
                .addPathItem("/api/v1/file/content", new PathItem().get(download)));

        new SpringDocConfig().openApiCustomizer().customise(openApi);

        assertThat(validation.getRequestBody().getContent()).containsKey("application/x-www-form-urlencoded");
        Schema<?> form = validation
                .getRequestBody()
                .getContent()
                .get("application/x-www-form-urlencoded")
                .getSchema();
        assertThat(form.getRequired()).containsExactlyInAnyOrder("token", "mobile");
        assertThat(download.getResponses().get("200").getContent()).containsKey("application/octet-stream");
        assertThat(download.getResponses()
                        .get("200")
                        .getContent()
                        .get("application/octet-stream")
                        .getSchema()
                        .getFormat())
                .isEqualTo("binary");
    }

    /**
     * 创建带摘要的 OpenAPI 操作。
     *
     * @param operationId 操作标识
     * @return OpenAPI 操作
     */
    private io.swagger.v3.oas.models.Operation operation(String operationId) {
        return new io.swagger.v3.oas.models.Operation()
                .operationId(operationId)
                .summary("测试接口")
                .responses(new ApiResponses());
    }

    /**
     * 创建单路径 OpenAPI 文档。
     *
     * @param path 接口路径
     * @param pathItem 路径操作
     * @return OpenAPI 文档
     */
    private OpenAPI openApi(String path, PathItem pathItem) {
        OpenAPI openApi = new SpringDocConfig().apexOpenApi();
        openApi.setPaths(new Paths().addPathItem(path, pathItem));
        return openApi;
    }
}
