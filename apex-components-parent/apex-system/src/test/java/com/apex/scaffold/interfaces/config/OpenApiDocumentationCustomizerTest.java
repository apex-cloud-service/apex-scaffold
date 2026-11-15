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
        assertThat(((Schema<?>) response.getProperties().get("code")).getExample())
                .isEqualTo(0);
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
        Schema<?> success = openApi.getComponents().getSchemas().get("ApiSuccessResponse");
        assertThat(((Schema<?>) success.getProperties().get("code")).getExample())
                .isEqualTo(0);
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
        OpenAPI openApi = new SpringDocConfig().apexOpenApi("apex-scaffold", "1.2.0");
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
     * 验证表单请求的 Content-Type。
     *
     * <p>文件下载能力已随文件域迁出至 apex-file-service，此处不再断言二进制响应。</p>
     */
    @Test
    void shouldDocumentFormRequest() {
        io.swagger.v3.oas.models.Operation validation = operation("validateMobile");
        OpenAPI openApi = new SpringDocConfig().apexOpenApi("apex-scaffold", "1.2.0");
        openApi.setPaths(new Paths().addPathItem("/api/v1/auth/cl/validate-mobile", new PathItem().post(validation)));

        new SpringDocConfig().openApiCustomizer().customise(openApi);

        assertThat(validation.getRequestBody().getContent()).containsKey("application/x-www-form-urlencoded");
        Schema<?> form = validation
                .getRequestBody()
                .getContent()
                .get("application/x-www-form-urlencoded")
                .getSchema();
        assertThat(form.getRequired()).containsExactlyInAnyOrder("token", "mobile");
    }

    @Test
    void shouldKeepRefreshTokenInJsonBodyOnly() {
        var refresh = operation("authRefresh");
        OpenAPI api = openApi("/api/v1/auth/refresh", new PathItem().post(refresh));

        new SpringDocConfig().openApiCustomizer().customise(api);

        assertThat(refresh.getParameters() == null || refresh.getParameters().isEmpty())
                .isTrue();
        assertThat(refresh.getRequestBody()
                        .getContent()
                        .get("application/json")
                        .getSchema()
                        .get$ref())
                .endsWith("/RefreshTokenRequest");
    }

    @Test
    void shouldDocumentOAuthRedirectWithoutSuccessEnvelope() {
        var authorize = operation("oauth2Authorize");
        authorize.getResponses().addApiResponse("302", new ApiResponse()).addApiResponse("200", new ApiResponse());
        var postAuthorize = operation("oauth2Authorize_1");
        OpenAPI api = openApi("/oauth2/authorize", new PathItem().get(authorize).post(postAuthorize));

        new SpringDocConfig().openApiCustomizer().customise(api);

        assertThat(authorize.getResponses()).containsKeys("307", "400").doesNotContainKeys("200", "302");
        assertThat(postAuthorize.getResponses()).containsKeys("307", "400").doesNotContainKey("200");
        assertThat(authorize.getResponses().get("307").getContent()).isNull();
        assertThat(authorize.getResponses().get("307").getHeaders()).containsKey("Location");
        assertThat(authorize.getParameters())
                .extracting(Parameter::getName)
                .containsExactlyInAnyOrder(
                        "response_type",
                        "client_id",
                        "redirect_uri",
                        "scope",
                        "state",
                        "code_challenge",
                        "code_challenge_method");
    }

    @Test
    void shouldDocumentSocialLoginRedirectsWithoutSuccessEnvelope() throws NoSuchMethodException {
        for (String operationId : Set.of("initiateOidcLogin", "initiateSocialLogin")) {
            var redirect = operation(operationId);
            redirect.getResponses().addApiResponse("302", new ApiResponse()).addApiResponse("200", new ApiResponse());
            String path = "initiateOidcLogin".equals(operationId)
                    ? "/api/v1/auth/oidc/login"
                    : "/api/v1/auth/social/{provider}/login";
            OpenAPI api = openApi(path, new PathItem().get(redirect));

            new SpringDocConfig().openApiCustomizer().customise(api);

            assertThat(redirect.getResponses()).containsKey("307").doesNotContainKeys("200", "302");
            assertThat(redirect.getResponses().get("307").getContent()).isNull();
            assertThat(redirect.getResponses()
                            .get("307")
                            .getHeaders()
                            .get("Location")
                            .getSchema()
                            .getType())
                    .isEqualTo("string");
            if ("initiateSocialLogin".equals(operationId)) {
                assertThat(responseSchema(redirect, "400").get$ref()).endsWith("/ApiErrorResponse");
            }
            var annotations = com.apex.scaffold.interfaces.handler.SocialLoginHandler.class
                    .getMethod(operationId, org.springframework.web.reactive.function.server.ServerRequest.class)
                    .getAnnotation(io.swagger.v3.oas.annotations.responses.ApiResponses.class);
            assertThat(annotations.value())
                    .extracting(io.swagger.v3.oas.annotations.responses.ApiResponse::responseCode)
                    .contains("307")
                    .doesNotContain("200", "302");
            assertThat(annotations.value())
                    .filteredOn(response -> "307".equals(response.responseCode()))
                    .allSatisfy(response -> {
                        assertThat(response.content()).isEmpty();
                        assertThat(response.headers()).singleElement().satisfies(header -> {
                            assertThat(header.name()).isEqualTo("Location");
                            assertThat(header.schema().type()).isEqualTo("string");
                        });
                    });
        }
    }

    @Test
    void shouldPreserveNativeOAuthResponsesAndResultEndpoints() {
        var token = operation("oauth2Token");
        var introspect = operation("oauth2Introspect");
        var revoke = operation("oauth2Revoke");
        var consent = operation("oauth2Consent");
        var revokeConsent = operation("oauth2RevokeConsent");
        for (var resultEndpoint : java.util.List.of(revoke, consent, revokeConsent)) {
            resultEndpoint
                    .getResponses()
                    .addApiResponse(
                            "200",
                            new ApiResponse()
                                    .content(new Content()
                                            .addMediaType(
                                                    "application/json",
                                                    new MediaType()
                                                            .schema(new Schema<>()
                                                                    .$ref("#/components/schemas/Result")))));
        }
        OpenAPI api = new SpringDocConfig().apexOpenApi("apex-scaffold", "1.2.0");
        api.setPaths(new Paths()
                .addPathItem("/oauth2/token", new PathItem().post(token))
                .addPathItem("/oauth2/introspect", new PathItem().post(introspect))
                .addPathItem("/oauth2/revoke", new PathItem().post(revoke))
                .addPathItem("/oauth2/consent", new PathItem().post(consent))
                .addPathItem("/oauth2/consent/revoke", new PathItem().post(revokeConsent)));

        new SpringDocConfig().openApiCustomizer().customise(api);

        assertThat(responseSchema(token, "200").getProperties())
                .containsOnlyKeys("access_token", "token_type", "expires_in", "refresh_token", "scope");
        assertThat(responseSchema(token, "400").getProperties()).containsOnlyKeys("error", "error_description");
        assertThat(token.getResponses()).doesNotContainKey("401");
        Schema<?> form = token.getRequestBody()
                .getContent()
                .get("application/x-www-form-urlencoded")
                .getSchema();
        assertThat(form.getProperties())
                .containsKeys(
                        "grant_type",
                        "client_id",
                        "client_secret",
                        "code",
                        "redirect_uri",
                        "code_verifier",
                        "username",
                        "password",
                        "refresh_token",
                        "scope");
        assertThat(form.getRequired()).containsExactly("grant_type");
        assertThat(responseSchema(introspect, "200").getProperties())
                .containsOnlyKeys("active", "client_id", "user_id", "scope", "exp");
        Schema<?> introspection = responseSchema(introspect, "200");
        assertThat(introspection.getRequired()).containsExactly("active");
        assertThat(((Schema<?>) introspection.getProperties().get("user_id")).getNullable())
                .isTrue();
        assertThat(((Schema<?>) introspection.getProperties().get("scope")).getNullable())
                .isTrue();
        assertThat(responseSchema(introspect, "400").get$ref()).endsWith("/ApiErrorResponse");
        for (var resultEndpoint : java.util.List.of(revoke, consent, revokeConsent)) {
            Schema<?> result = responseSchema(resultEndpoint, "200");
            assertThat(result.getProperties()).containsOnlyKeys("code", "msg", "data", "traceId");
            assertThat(((Schema<?>) result.getProperties().get("data")).getType())
                    .isEqualTo("string");
            assertThat(((Schema<?>) result.getProperties().get("code")).getExample())
                    .isEqualTo(0);
        }
    }

    /** 统一认证收口（A5）：system 侧 jwks 空壳端点已随收口移除，discovery 原生 JSON 语义保留。 */
    @Test
    void shouldKeepDiscoveryAsNativeJson() {
        var discovery = operation("oauth2Discovery");
        OpenAPI api = new SpringDocConfig().apexOpenApi("apex-scaffold", "1.2.0");
        api.setPaths(new Paths().addPathItem("/.well-known/oauth-authorization-server", new PathItem().get(discovery)));

        new SpringDocConfig().openApiCustomizer().customise(api);

        assertThat(responseSchema(discovery, "200").getProperties())
                .containsKeys("issuer", "authorization_endpoint", "token_endpoint", "scopes_supported")
                .doesNotContainKeys("code", "data");
    }

    @Test
    void shouldDocumentConcreteSearchSsoAndKeycloakData() {
        var search = operation("unifiedSearch");
        var rebuild = operation("rebuildSearchIndex");
        var sso = operation("handleSsoCallback");
        var keycloak = operation("syncKeycloakUser");
        OpenAPI api = new SpringDocConfig().apexOpenApi("apex-scaffold", "1.2.0");
        api.setPaths(new Paths()
                .addPathItem("/api/v1/search", new PathItem().get(search))
                .addPathItem("/admin-api/v1/search/rebuild", new PathItem().post(rebuild))
                .addPathItem("/api/v1/auth/sso/callback", new PathItem().get(sso))
                .addPathItem("/admin-api/v1/keycloak/sync/user/{keycloakSub}", new PathItem().post(keycloak)));

        new SpringDocConfig().openApiCustomizer().customise(api);

        Schema<?> pageReference =
                (Schema<?>) responseSchema(search, "200").getProperties().get("data");
        Schema<?> page = api.getComponents()
                .getSchemas()
                .get(pageReference.get$ref().substring("#/components/schemas/".length()));
        assertThat(page.getProperties()).containsKeys("list", "total", "pageNum", "pageSize", "pages", "hasNext");
        Schema<?> list = (Schema<?>) page.getProperties().get("list");
        assertThat(list.getItems().get$ref()).endsWith("/SearchResultDTO");
        assertThat(((Schema<?>) responseSchema(rebuild, "200").getProperties().get("data")).getFormat())
                .isEqualTo("int64");
        Schema<?> ssoData =
                (Schema<?>) responseSchema(sso, "200").getProperties().get("data");
        assertThat(ssoData.getProperties()).containsOnlyKeys("sessionId", "token", "userInfo", "expiryTime");
        assertThat(((Schema<?>) ssoData.getProperties().get("token")).getDescription())
                .contains("非 JWT");
        assertThat(((Schema<?>) responseSchema(keycloak, "200").getProperties().get("data")).getProperties())
                .containsOnlyKeys("userId", "username", "email");
    }

    private Schema<?> responseSchema(io.swagger.v3.oas.models.Operation operation, String status) {
        return operation
                .getResponses()
                .get(status)
                .getContent()
                .get("application/json")
                .getSchema();
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
        OpenAPI openApi = new SpringDocConfig().apexOpenApi("apex-scaffold", "1.2.0");
        openApi.setPaths(new Paths().addPathItem(path, pathItem));
        return openApi;
    }
}
