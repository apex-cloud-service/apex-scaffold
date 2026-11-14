package com.apex.scaffold.interfaces.config;

import com.apex.scaffold.application.dto.AccountDeletionRequest;
import com.apex.scaffold.application.dto.LoginRequest;
import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.application.dto.MobileValidateResponse;
import com.apex.scaffold.application.dto.PageRequest;
import com.apex.scaffold.application.dto.PageResponse;
import com.apex.scaffold.application.dto.PermissionDTO;
import com.apex.scaffold.application.dto.PermissionRequest;
import com.apex.scaffold.application.dto.RefreshTokenRequest;
import com.apex.scaffold.application.dto.RegisterRequest;
import com.apex.scaffold.application.dto.RoleDTO;
import com.apex.scaffold.application.dto.RoleRequest;
import com.apex.scaffold.application.dto.SendSmsRequest;
import com.apex.scaffold.application.dto.UserDTO;
import com.apex.scaffold.application.dto.WechatBindPhoneRequest;
import com.apex.scaffold.application.dto.WxPhoneRequest;
import com.apex.scaffold.application.dto.admin.AdminRoleResponse;
import com.apex.scaffold.application.dto.admin.AdminUserCreateRequest;
import com.apex.scaffold.application.dto.admin.AdminUserDetailResponse;
import com.apex.scaffold.application.dto.admin.AdminUserRolesRequest;
import com.apex.scaffold.application.dto.admin.AdminUserStatusRequest;
import com.apex.scaffold.application.dto.admin.AdminUserSummaryResponse;
import com.apex.scaffold.application.dto.admin.AdminUserUpdateRequest;
import com.apex.scaffold.application.dto.admin.ApiCallLogDTO;
import com.apex.scaffold.application.dto.admin.AuditLogDTO;
import com.apex.scaffold.application.dto.admin.ConfigDTO;
import com.apex.scaffold.application.dto.admin.ConfigRequest;
import com.apex.scaffold.application.dto.admin.DeptDTO;
import com.apex.scaffold.application.dto.admin.DictDataDTO;
import com.apex.scaffold.application.dto.admin.DictDataRequest;
import com.apex.scaffold.application.dto.admin.DictTypeDTO;
import com.apex.scaffold.application.dto.admin.DictTypeRequest;
import com.apex.scaffold.application.dto.admin.JobDTO;
import com.apex.scaffold.application.dto.admin.JobLogDTO;
import com.apex.scaffold.application.dto.admin.JobRequest;
import com.apex.scaffold.application.dto.admin.LoginLogDTO;
import com.apex.scaffold.application.dto.admin.MenuDTO;
import com.apex.scaffold.application.dto.admin.MenuRequest;
import com.apex.scaffold.application.dto.admin.OnlineUserDTO;
import com.apex.scaffold.application.dto.admin.StatusRequest;
import com.apex.scaffold.application.dto.search.SearchResultDTO;
import com.apex.scaffold.interfaces.dto.monitor.MonitorBusinessResponse;
import com.apex.scaffold.interfaces.dto.monitor.MonitorGovernanceResponse;
import com.apex.scaffold.interfaces.dto.monitor.MonitorMiddlewareResponse;
import com.apex.scaffold.interfaces.dto.monitor.MonitorOverviewResponse;
import com.apex.scaffold.interfaces.dto.monitor.MonitorSystemResponse;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.ParameterizedTypeReference;

/**
 * 函数式接口 OpenAPI 文档增强器。
 *
 * <p>函数式 WebFlux Handler 对外只暴露 {@code ServerRequest} 和 {@code ServerResponse}，
 * SpringDoc 无法像控制器方法一样自动推断业务 DTO。本增强器集中维护请求模型和查询参数，
 * 并为所有接口补充统一响应结构、媒体类型、示例及错误响应。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
final class OpenApiDocumentationCustomizer {

    /** 路径模板变量匹配表达式，用于提取路径中的 {variableName} 形式的变量。 */
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^}/]+)}");

    /** OpenAPI 统一成功响应组件名称，用于所有接口成功响应引用的公共 schema。 */
    private static final String SUCCESS_SCHEMA = "ApiSuccessResponse";

    /** OpenAPI 统一错误响应组件名称，用于所有接口错误响应引用的公共 schema。 */
    private static final String ERROR_SCHEMA = "ApiErrorResponse";

    /** 使用 202 表示已受理异步处理的操作。 */
    private static final Set<String> ASYNC_ACCEPTED_OPERATION_IDS = Set.of("deleteCurrentUser");

    /** 以 JSON 形式接收 DTO 的操作。 */
    private static final Map<String, Class<?>> JSON_REQUEST_TYPES = Map.ofEntries(
            Map.entry("authLogin", LoginRequest.class),
            Map.entry("authRefresh", RefreshTokenRequest.class),
            Map.entry("authRegister", RegisterRequest.class),
            Map.entry("bindWechatPhone", WechatBindPhoneRequest.class),
            Map.entry("sendSmsCode", SendSmsRequest.class),
            Map.entry("sendClSmsCode", SendSmsRequest.class),
            Map.entry("bindPhone", WxPhoneRequest.class),
            Map.entry("createRole", RoleRequest.class),
            Map.entry("updateRole", RoleRequest.class),
            Map.entry("createPermission", PermissionRequest.class),
            Map.entry("updatePermission", PermissionRequest.class),
            Map.entry("listUsers", PageRequest.class),
            Map.entry("deleteCurrentUser", AccountDeletionRequest.class),
            Map.entry("adminCreateUser", AdminUserCreateRequest.class),
            Map.entry("adminUpdateUser", AdminUserUpdateRequest.class),
            Map.entry("adminReplaceUserRoles", AdminUserRolesRequest.class),
            Map.entry("adminUpdateUserStatus", AdminUserStatusRequest.class),
            Map.entry("adminCreateMenu", MenuRequest.class),
            Map.entry("adminUpdateMenu", MenuRequest.class),
            Map.entry("adminUpdateMenuStatus", StatusRequest.class),
            Map.entry("adminCreateDictType", DictTypeRequest.class),
            Map.entry("adminUpdateDictType", DictTypeRequest.class),
            Map.entry("adminUpdateDictTypeStatus", StatusRequest.class),
            Map.entry("adminCreateDictData", DictDataRequest.class),
            Map.entry("adminUpdateDictData", DictDataRequest.class),
            Map.entry("adminUpdateDictDataStatus", StatusRequest.class),
            Map.entry("adminCreateConfig", ConfigRequest.class),
            Map.entry("adminUpdateConfig", ConfigRequest.class),
            Map.entry("adminUpdateConfigStatus", StatusRequest.class),
            Map.entry("adminCreateJob", JobRequest.class),
            Map.entry("adminUpdateJob", JobRequest.class),
            Map.entry("adminUpdateJobStatus", StatusRequest.class),
            Map.entry("adminCreateDept", DeptDTO.class),
            Map.entry("adminUpdateDept", DeptDTO.class));

    /** 操作对应的查询参数定义，key 为 operationId，value 为该操作支持的查询参数列表。 */
    private static final Map<String, List<QueryParameter>> QUERY_PARAMETERS = queryParameters();

    /** 操作对应的请求头参数定义，key 为 operationId，value 为该操作需要声明的请求头参数。 */
    private static final Map<String, List<HeaderParameter>> HEADER_PARAMETERS = Map.ofEntries(Map.entry(
            "deleteCurrentUser", List.of(new HeaderParameter("Idempotency-Key", "可选，客户端重试应复用同一值；不可跨账号复用", false))));

    /** 操作对应的成功响应 data 数据类型。 */
    private static final Map<String, Class<?>> RESPONSE_TYPES = Map.ofEntries(
            // 认证与授权
            Map.entry("authLogin", LoginResponse.class),
            Map.entry("authRegister", LoginResponse.class),
            Map.entry("bindWechatPhone", LoginResponse.class),
            Map.entry("authRefresh", LoginResponse.class),
            Map.entry("oauth2Revoke", String.class),
            Map.entry("oauth2Consent", String.class),
            Map.entry("oauth2RevokeConsent", String.class),
            Map.entry("validateMobile", MobileValidateResponse.class),
            Map.entry("getMyRoles", RoleDTO.class),
            Map.entry("getAllRoles", RoleDTO.class),
            Map.entry("createRole", RoleDTO.class),
            Map.entry("updateRole", RoleDTO.class),
            Map.entry("getMyPermissions", PermissionDTO.class),
            Map.entry("getAllPermissions", PermissionDTO.class),
            Map.entry("createPermission", PermissionDTO.class),
            Map.entry("updatePermission", PermissionDTO.class),
            // 用户与个人资料
            Map.entry("getCurrentUserInfo", UserDTO.class),
            Map.entry("getUserById", UserDTO.class),
            Map.entry("listUsers", PageResponse.class),
            // 后台用户管理
            Map.entry("adminCreateUser", AdminUserDetailResponse.class),
            Map.entry("adminListUsers", AdminUserSummaryResponse.class),
            Map.entry("adminGetUser", AdminUserDetailResponse.class),
            Map.entry("adminUpdateUser", AdminUserDetailResponse.class),
            Map.entry("adminReplaceUserRoles", AdminUserDetailResponse.class),
            Map.entry("adminUpdateUserStatus", AdminUserDetailResponse.class),
            Map.entry("adminListStaffRoles", AdminRoleResponse.class),
            Map.entry("adminListMenus", MenuDTO.class),
            Map.entry("adminGetMenu", MenuDTO.class),
            Map.entry("adminCreateMenu", MenuDTO.class),
            Map.entry("adminUpdateMenu", MenuDTO.class),
            Map.entry("adminUpdateMenuStatus", MenuDTO.class),
            Map.entry("adminListDictTypes", DictTypeDTO.class),
            Map.entry("adminGetDictType", DictTypeDTO.class),
            Map.entry("adminCreateDictType", DictTypeDTO.class),
            Map.entry("adminUpdateDictType", DictTypeDTO.class),
            Map.entry("adminListDictData", DictDataDTO.class),
            Map.entry("adminGetDictData", DictDataDTO.class),
            Map.entry("adminCreateDictData", DictDataDTO.class),
            Map.entry("adminUpdateDictData", DictDataDTO.class),
            Map.entry("adminListConfigs", ConfigDTO.class),
            Map.entry("adminGetConfig", ConfigDTO.class),
            Map.entry("adminCreateConfig", ConfigDTO.class),
            Map.entry("adminUpdateConfig", ConfigDTO.class),
            Map.entry("adminListJobs", JobDTO.class),
            Map.entry("adminGetJob", JobDTO.class),
            Map.entry("adminCreateJob", JobDTO.class),
            Map.entry("adminUpdateJob", JobDTO.class),
            Map.entry("adminListJobLogs", JobLogDTO.class),
            Map.entry("adminListLoginLogs", LoginLogDTO.class),
            Map.entry("adminListAuditLogs", AuditLogDTO.class),
            Map.entry("adminListApiCallLogs", ApiCallLogDTO.class),
            Map.entry("adminListOnlineUsers", OnlineUserDTO.class),
            Map.entry("adminListDepts", DeptDTO.class),
            Map.entry("adminGetDept", DeptDTO.class),
            Map.entry("adminCreateDept", DeptDTO.class),
            Map.entry("adminUpdateDept", DeptDTO.class),
            Map.entry("monitorOverview", MonitorOverviewResponse.class),
            Map.entry("monitorSystem", MonitorSystemResponse.class),
            Map.entry("monitorMiddleware", MonitorMiddlewareResponse.class),
            Map.entry("monitorGovernance", MonitorGovernanceResponse.class),
            Map.entry("monitorBusiness", MonitorBusinessResponse.class));

    /** 响应 data 为列表类型的操作。 */
    private static final Set<String> LIST_RESPONSE_TYPES = Set.of(
            "getMyRoles",
            "getAllRoles",
            "getMyPermissions",
            "getAllPermissions",
            "listUsers",
            "adminListMenus",
            "adminListDictTypes",
            "adminListDictData",
            "adminListConfigs",
            "adminListJobs",
            "adminListJobLogs",
            "adminListLoginLogs",
            "adminListAuditLogs",
            "adminListApiCallLogs",
            "adminListOnlineUsers",
            "adminListDepts");

    /** 特殊操作 ID 常量 */
    private static final String OP_ID_VALIDATE_MOBILE = "validateMobile";

    private static final String OP_ID_STREAM_NOTIFICATIONS = "streamNotifications";
    private static final String OP_ID_AUTH_LOGIN = "authLogin";
    private static final String OP_ID_DELETE_CURRENT_USER = "deleteCurrentUser";
    private static final String OP_ID_SEND_ACCOUNT_DELETION_CODE = "sendAccountDeletionCode";

    /** 发起社交登录的操作标识。 */
    private static final String OP_ID_INITIATE_SOCIAL_LOGIN = "initiateSocialLogin";

    /** 统一搜索的操作标识。 */
    private static final String OP_ID_UNIFIED_SEARCH = "unifiedSearch";

    /** 重建搜索索引的操作标识。 */
    private static final String OP_ID_REBUILD_SEARCH_INDEX = "rebuildSearchIndex";

    /** 处理单点登录回调的操作标识。 */
    private static final String OP_ID_HANDLE_SSO_CALLBACK = "handleSsoCallback";

    /** 同步 Keycloak 用户的操作标识。 */
    private static final String OP_ID_SYNC_KEYCLOAK_USER = "syncKeycloakUser";

    /** 匿名访问路径判断器，用于确定哪些接口无需 Bearer Token 鉴权。 */
    private final Predicate<String> publicPath;

    /** 按固定顺序执行的操作文档贡献器链，依次对每个操作应用自定义增强逻辑。 */
    private final List<OpenApiOperationContributor> contributors;

    /**
     * 创建文档增强器。
     *
     * @param publicPath 匿名访问路径判断器
     */
    OpenApiDocumentationCustomizer(Predicate<String> publicPath) {
        this.publicPath = publicPath;
        this.contributors = List.of(
                context -> {
                    enrichDescription(context.operation());
                    addPathParameters(context.path(), context.operation());
                },
                context -> {
                    if (context.operationId() != null && !context.operationId().isBlank()) {
                        addQueryParameters(context.operationId(), context.operation());
                        addHeaderParameters(context.operationId(), context.operation());
                        addRequestBody(context.operationId(), context.operation(), context.components());
                    }
                },
                context -> addResponses(
                        context.operationId(), context.operation(), context.publicPath(), context.components()));
    }

    /**
     * 增强完整 OpenAPI 文档。
     *
     * @param openApi SpringDoc 已生成的 OpenAPI 文档
     */
    void customise(OpenAPI openApi) {
        if (openApi == null || openApi.getPaths() == null) {
            return;
        }
        Components components = openApi.getComponents() == null ? new Components() : openApi.getComponents();
        openApi.setComponents(components);
        registerResponseSchemas(components);

        openApi.getPaths().forEach((path, pathItem) -> pathItem.readOperations().forEach(operation -> {
            OpenApiOperationContext context = new OpenApiOperationContext(
                    path,
                    "/oauth2/authorize".equals(path) ? "oauth2Authorize" : operation.getOperationId(),
                    operation,
                    components,
                    publicPath.test(path));
            contributors.forEach(contributor -> contributor.contribute(context));
        }));
    }

    /**
     * 注册统一成功与错误响应模型。
     *
     * @param components OpenAPI 组件容器
     */
    private void registerResponseSchemas(Components components) {
        components.addSchemas(SUCCESS_SCHEMA, resultSchema("业务数据；具体字段见接口成功响应模型", false));
        components.addSchemas(ERROR_SCHEMA, resultSchema("错误详情或空值", true));
    }

    /**
     * 构造统一响应模型。
     *
     * @param dataDescription data 字段说明
     * @param error 是否为错误响应
     * @return 响应对象 Schema
     */
    private ObjectSchema resultSchema(String dataDescription, boolean error) {
        ObjectSchema schema = new ObjectSchema();
        schema.setDescription(error ? "统一错误响应" : "统一成功响应");
        schema.addProperty(
                "code", new IntegerSchema().format("int32").description("业务状态码").example(error ? 400 : 0));
        schema.addProperty("msg", new StringSchema().description("面向调用方的结果说明").example(error ? "请求参数不正确" : "操作成功"));
        schema.addProperty(
                "data", new ObjectSchema().description(dataDescription).nullable(true));
        schema.addProperty(
                "traceId", new StringSchema().description("链路追踪 ID，反馈问题时请一并提供").example("01JAZ8Y7N2M4P6Q8R0STUVWX12"));
        return schema;
    }

    /**
     * 补充便于阅读的操作说明。
     *
     * @param operation OpenAPI 操作
     */
    private void enrichDescription(io.swagger.v3.oas.models.Operation operation) {
        if (operation.getDescription() == null || operation.getDescription().isBlank()) {
            String summary =
                    operation.getSummary() == null || operation.getSummary().isBlank()
                            ? "执行该接口"
                            : operation.getSummary();
            operation.setDescription(summary + "。成功响应包含业务状态码、提示信息、业务数据和 traceId；出现问题时可使用 traceId 定位请求链路。");
        }
    }

    /**
     * 根据 URI 模板自动补齐路径参数。
     *
     * @param path 接口路径
     * @param operation OpenAPI 操作
     */
    private void addPathParameters(String path, io.swagger.v3.oas.models.Operation operation) {
        Matcher matcher = PATH_VARIABLE.matcher(path);
        while (matcher.find()) {
            String name = matcher.group(1);
            Schema<?> schema = pathParameterSchema(name);
            Parameter parameter = findParameter(operation, name, "path");
            if (parameter == null) {
                parameter = new Parameter().name(name).in("path");
                operation.addParametersItem(parameter);
            }
            parameter.setRequired(true);
            parameter.setSchema(schema);
            if (parameter.getDescription() == null || parameter.getDescription().isBlank()) {
                parameter.setDescription(humanizeParameter(name));
            }
        }
    }

    /**
     * 根据路径参数语义补齐稳定类型，避免把追踪 ID 误判为数字。
     *
     * @param name 路径变量名
     * @return 路径参数 schema
     */
    private Schema<?> pathParameterSchema(String name) {
        return switch (name) {
            case "userId", "roleId", "id" -> new IntegerSchema().format("int64").minimum(java.math.BigDecimal.ONE);
            default -> new StringSchema();
        };
    }

    /**
     * 补齐 Handler 实际读取的查询参数。
     *
     * @param operationId 操作唯一标识
     * @param operation OpenAPI 操作
     */
    private void addQueryParameters(String operationId, io.swagger.v3.oas.models.Operation operation) {
        List<QueryParameter> definitions = "oauth2Authorize".equals(operationId)
                ? List.of(
                        stringQuery("response_type", "授权响应类型，仅支持 code", true, null, List.of("code")),
                        stringQuery("client_id", "客户端标识", true, null, List.of()),
                        stringQuery("redirect_uri", "客户端回调地址", true, null, List.of()),
                        stringQuery("scope", "请求作用域", false, null, List.of()),
                        stringQuery("state", "客户端状态值，重定向时原样返回", false, null, List.of()),
                        stringQuery("code_challenge", "PKCE 挑战值", false, null, List.of()),
                        stringQuery("code_challenge_method", "PKCE 挑战方法", false, null, List.of()))
                : QUERY_PARAMETERS.getOrDefault(operationId, List.of());
        for (QueryParameter definition : definitions) {
            Parameter parameter = findParameter(operation, definition.name(), "query");
            if (parameter == null) {
                operation.addParametersItem(definition.toOpenApiParameter());
                continue;
            }
            parameter.setRequired(definition.required());
            parameter.setSchema(definition.schema());
            if (parameter.getDescription() == null || parameter.getDescription().isBlank()) {
                parameter.setDescription(definition.description());
            }
        }
    }

    /**
     * 补齐 Handler 实际读取的请求头参数。
     *
     * @param operationId 操作唯一标识
     * @param operation OpenAPI 操作
     */
    private void addHeaderParameters(String operationId, io.swagger.v3.oas.models.Operation operation) {
        for (HeaderParameter definition : HEADER_PARAMETERS.getOrDefault(operationId, List.of())) {
            if (findParameter(operation, definition.name(), "header") == null) {
                operation.addParametersItem(definition.toOpenApiParameter());
            }
        }
    }

    /**
     * 补齐 JSON、表单及结构化自由对象请求体。
     *
     * @param operationId 操作唯一标识
     * @param operation OpenAPI 操作
     * @param components OpenAPI 组件容器
     */
    private void addRequestBody(
            String operationId, io.swagger.v3.oas.models.Operation operation, Components components) {
        Schema<?> oauthForm = oauthFormSchema(operationId);
        if (oauthForm != null) {
            operation.setRequestBody(requestBody(
                    "OAuth2 表单参数；令牌模式专属字段仅在对应 grant_type 下必填", "application/x-www-form-urlencoded", oauthForm));
            return;
        }
        if (hasValidRequestBodySchema(operation.getRequestBody())) {
            ensureRequestBodyDescription(operation.getRequestBody());
            return;
        }
        Class<?> requestType = JSON_REQUEST_TYPES.get(operationId);
        if (requestType != null) {
            operation.setRequestBody(
                    requestBody("JSON 请求参数", "application/json", resolveSchema(requestType, components)));
            return;
        }
        Schema<?> schema = specialRequestSchema(operationId);
        if (schema != null) {
            operation.setRequestBody(requestBody("JSON 请求参数", "application/json", schema));
            return;
        }
        if (OP_ID_VALIDATE_MOBILE.equals(operationId)) {
            operation.setRequestBody(requestBody(
                    "本机号校验表单",
                    "application/x-www-form-urlencoded",
                    objectSchema(
                            Map.of(
                                    "token", requiredString("运营商认证 SDK 返回的一次性令牌"),
                                    "mobile", requiredString("待校验手机号"),
                                    "outId", new StringSchema().description("调用方业务流水号")),
                            Set.of("token", "mobile"))));
        }
    }

    /**
     * 按 Handler 实际读取字段构造 OAuth2 表单。
     *
     * @param operationId 操作唯一标识
     * @return OAuth2 表单模型；非表单操作或标识为空时返回 {@code null}
     */
    private Schema<?> oauthFormSchema(String operationId) {
        if (operationId == null) {
            return null;
        }
        return switch (operationId) {
            case "oauth2Token" -> {
                StringSchema grantType = new StringSchema();
                grantType.setEnum(List.of("authorization_code", "client_credentials", "password", "refresh_token"));
                Map<String, Schema<?>> properties = new LinkedHashMap<>();
                properties.put("grant_type", grantType);
                properties.put("client_id", new StringSchema().description("客户端标识"));
                properties.put("client_secret", new StringSchema().description("客户端密钥"));
                properties.put("code", new StringSchema().description("authorization_code 模式的授权码"));
                properties.put("redirect_uri", new StringSchema().description("authorization_code 模式的回调地址"));
                properties.put("code_verifier", new StringSchema().description("PKCE 校验值"));
                properties.put("username", new StringSchema().description("password 模式用户名"));
                properties.put("password", new StringSchema().description("password 模式密码"));
                properties.put("refresh_token", new StringSchema().description("refresh_token 模式刷新令牌"));
                properties.put("scope", new StringSchema().description("client_credentials/password 模式作用域"));
                yield objectSchema(properties, Set.of("grant_type"));
            }
            case "oauth2Revoke" -> objectSchema(
                    Map.of("token", new StringSchema(), "client_id", new StringSchema()), Set.of("token", "client_id"));
            case "oauth2Introspect" -> objectSchema(Map.of("token", new StringSchema()), Set.of("token"));
            case "oauth2Consent" -> {
                StringSchema approved = new StringSchema();
                approved.setEnum(List.of("true", "false"));
                yield objectSchema(
                        Map.of("client_id", new StringSchema(), "scope", new StringSchema(), "approved", approved),
                        Set.of("client_id", "approved"));
            }
            case "oauth2RevokeConsent" -> objectSchema(Map.of("client_id", new StringSchema()), Set.of("client_id"));
            default -> null;
        };
    }

    /**
     * 仅对原生 OAuth2 响应应用协议模型，保留撤销及同意端点的 Result 契约。
     *
     * @param operationId 操作唯一标识
     * @param operation OpenAPI 操作
     * @return 已应用原生协议响应模型时返回 {@code true}，否则返回 {@code false}
     */
    private boolean addOauthResponses(String operationId, io.swagger.v3.oas.models.Operation operation) {
        if (operationId == null) {
            return false;
        }
        ApiResponses responses = operation.getResponses();
        switch (operationId) {
            case "initiateOidcLogin", OP_ID_INITIATE_SOCIAL_LOGIN -> {
                responses.remove("200");
                responses.remove("302");
                responses.addApiResponse(
                        "307",
                        new ApiResponse()
                                .description("重定向到 OAuth2 提供商登录页面")
                                .addHeaderObject(
                                        "Location",
                                        new io.swagger.v3.oas.models.headers.Header().schema(new StringSchema())));
                if (OP_ID_INITIATE_SOCIAL_LOGIN.equals(operationId)) {
                    addErrorResponse(responses, "400", "不支持的社交登录提供商");
                }
                return true;
            }
            case "oauth2Authorize" -> {
                responses.remove("200");
                responses.remove("302");
                responses.addApiResponse(
                        "307",
                        new ApiResponse()
                                .description("重定向到客户端，携带 code/state 或 error/error_description/state")
                                .addHeaderObject(
                                        "Location",
                                        new io.swagger.v3.oas.models.headers.Header().schema(new StringSchema())));
                addErrorResponse(responses, "400", "缺少 client_id 或 redirect_uri 时返回 Result 错误");
                return true;
            }
            case "oauth2Token" -> {
                addOauthTokenResponses(responses);
                return true;
            }
            case "oauth2Introspect" -> {
                addOauthIntrospectionResponses(responses);
                return true;
            }
            case "oauth2Discovery" -> {
                addOauthDiscoveryResponse(responses);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * 补充令牌颁发成功及参数、授权或客户端认证失败的原生响应。
     *
     * @param responses 操作响应集合
     */
    private void addOauthTokenResponses(ApiResponses responses) {
        responses.remove("401");
        responses.addApiResponse(
                "200",
                jsonResponse(
                        "令牌颁发成功",
                        objectSchema(
                                Map.of(
                                        "access_token",
                                        new StringSchema(),
                                        "token_type",
                                        new StringSchema(),
                                        "expires_in",
                                        new IntegerSchema().format("int64"),
                                        "refresh_token",
                                        new StringSchema().nullable(true),
                                        "scope",
                                        new StringSchema().nullable(true)),
                                Set.of("access_token", "token_type", "expires_in", "refresh_token", "scope"))));
        responses.addApiResponse(
                "400",
                jsonResponse(
                        "参数、授权或客户端认证失败均返回 HTTP 400",
                        objectSchema(
                                Map.of("error", new StringSchema(), "error_description", new StringSchema()),
                                Set.of("error", "error_description"))));
    }

    /**
     * 补充令牌内省元数据及缺少令牌时的错误响应。
     *
     * @param responses 操作响应集合
     */
    private void addOauthIntrospectionResponses(ApiResponses responses) {
        responses.addApiResponse(
                "200",
                jsonResponse(
                        "令牌元数据；无效令牌仅返回 active=false",
                        objectSchema(
                                Map.of(
                                        "active",
                                        new BooleanSchema(),
                                        "client_id",
                                        new StringSchema(),
                                        "user_id",
                                        new StringSchema().nullable(true),
                                        "scope",
                                        new StringSchema().nullable(true),
                                        "exp",
                                        new IntegerSchema().format("int64")),
                                Set.of("active"))));
        addErrorResponse(responses, "400", "缺少 token，返回 Result 错误");
    }

    /**
     * 补充授权服务器发现文档的端点和能力元数据响应。
     *
     * @param responses 操作响应集合
     */
    private void addOauthDiscoveryResponse(ApiResponses responses) {
        ObjectSchema metadata = new ObjectSchema();
        for (String field : List.of(
                "issuer",
                "authorization_endpoint",
                "token_endpoint",
                "jwks_uri",
                "revocation_endpoint",
                "introspection_endpoint")) {
            metadata.addProperty(field, new StringSchema());
        }
        for (String field : List.of(
                "token_endpoint_auth_methods_supported",
                "response_types_supported",
                "grant_types_supported",
                "revocation_endpoint_auth_methods_supported",
                "introspection_endpoint_auth_methods_supported",
                "code_challenge_methods_supported",
                "scopes_supported")) {
            metadata.addProperty(field, new ArraySchema().items(new StringSchema()));
        }
        responses.addApiResponse("200", jsonResponse("OAuth2 授权服务器元数据", metadata));
    }

    /**
     * 构造原生 JSON 响应，不添加业务包装。
     *
     * @param description 响应说明
     * @param schema 响应模型
     * @return 原生 JSON 响应声明
     */
    private ApiResponse jsonResponse(String description, Schema<?> schema) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json", new MediaType().schema(schema)));
    }

    /**
     * 构造少量无专用 DTO 的请求模型。
     *
     * @param operationId 操作唯一标识
     * @return 请求 Schema；无需补充时返回 {@code null}
     */
    private Schema<?> specialRequestSchema(String operationId) {
        return switch (operationId) {
            case "deleteCurrentUser" -> {
                StringSchema code = new StringSchema();
                code.setDescription("注销二次验证短信验证码，6 位数字；验证码错误或过期返回 400");
                code.setExample("123456");
                code.setMinLength(6);
                code.setMaxLength(6);
                code.setPattern("\\d{6}");
                yield objectSchema(Map.of("code", code), Set.of("code"));
            }
            case "authLogin" -> {
                yield new ComposedSchema()
                        .oneOf(List.of(
                                loginVariantSchema(
                                        "WX_CODE",
                                        "微信授权码登录：需提供微信授权码 code",
                                        Map.of("code", requiredString("微信授权码（code），用户同意授权后微信返回，有效期5分钟")),
                                        Set.of("code")),
                                loginVariantSchema(
                                        "WX_PHONE",
                                        "微信一键登录（手机号）：需提供手机号授权凭证 phoneCode",
                                        Map.of("phoneCode", requiredString("手机号授权凭证（phoneCode），App端通过微信SDK获取")),
                                        Set.of("phoneCode")),
                                loginVariantSchema(
                                        "WX_SMS",
                                        "微信短信验证码登录：需提供手机号和短信验证码",
                                        Map.of(
                                                "phone", requiredString("手机号码"),
                                                "smsCode", requiredString("短信验证码，6位数字")),
                                        Set.of("phone", "smsCode")),
                                clPhoneLoginSchema(),
                                loginVariantSchema(
                                        "CL_SMS",
                                        "创蓝短信验证码登录：需提供手机号和短信验证码",
                                        Map.of(
                                                "phone", requiredString("手机号码"),
                                                "smsCode", requiredString("短信验证码，6位数字")),
                                        Set.of("phone", "smsCode"))))
                        .description("统一登录接口。根据 loginType 字段选择对应的登录参数组合：\n"
                                + "WX_CODE — 微信授权码登录\n"
                                + "WX_PHONE — 微信一键登录\n"
                                + "WX_SMS — 微信短信验证码登录\n"
                                + "CL_PHONE — 创蓝一键登录（闪验）\n"
                                + "CL_SMS — 创蓝短信验证码登录");
            }
            default -> null;
        };
    }

    /**
     * 构造单个登录类型的 Schema 变体，用于 {@code authLogin} 的 {@code oneOf}。
     *
     * @param loginType 登录类型枚举值，如 WX_CODE、WX_SMS
     * @param description 该登录类型的说明
     * @param typeSpecificFields 该登录类型特有的字段
     * @param typeSpecificRequired 该登录类型必填的字段（loginType 自动加入）
     * @return 包含 loginType 枚举 + 特有字段 + 公共可选字段的 Schema
     */
    private ObjectSchema loginVariantSchema(
            String loginType,
            String description,
            Map<String, Schema<?>> typeSpecificFields,
            Set<String> typeSpecificRequired) {
        Map<String, Schema<?>> allFields = new LinkedHashMap<>();
        allFields.put("loginType", new StringSchema().addEnumItem(loginType)._default(loginType));
        allFields.putAll(typeSpecificFields);
        allFields.put("scene", new StringSchema().description("场景标识，用于统计用户来源"));
        allFields.put("deviceId", new StringSchema().description("设备标识，绑定登录设备"));
        allFields.put("inviteUserId", new StringSchema().description("邀请人用户ID（选填）"));
        ObjectSchema schema = new ObjectSchema();
        schema.setDescription(description);
        allFields.forEach(schema::addProperty);
        List<String> required = new ArrayList<>();
        required.add("loginType");
        required.addAll(typeSpecificRequired);
        schema.setRequired(required);
        return schema;
    }

    /**
     * 构造创蓝一键登录（闪验）的 Schema。
     *
     * @return CL_PHONE 登录 Schema
     */
    private ObjectSchema clPhoneLoginSchema() {
        Map<String, Schema<?>> fields = new LinkedHashMap<>();
        fields.put("loginType", new StringSchema().addEnumItem("CL_PHONE")._default("CL_PHONE"));
        fields.put("clToken", requiredString("创蓝闪验Token，App端集成创蓝SDK后授权返回"));
        fields.put(
                "platform",
                new StringSchema().description("客户端平台标识：android / ios / h5 / harmony / wechat，为空时默认 android"));
        fields.put("scene", new StringSchema().description("场景标识"));
        fields.put("deviceId", new StringSchema().description("设备标识"));
        fields.put("inviteUserId", new StringSchema().description("邀请人用户ID（选填）"));
        ObjectSchema schema = new ObjectSchema();
        schema.setDescription("创蓝一键登录（闪验）：需提供 clToken 和 platform");
        fields.forEach(schema::addProperty);
        schema.setRequired(List.of("loginType", "clToken"));
        return schema;
    }

    /**
     * 为所有操作补齐成功响应、媒体类型和统一错误响应。
     *
     * @param operationId 操作唯一标识
     * @param operation OpenAPI 操作
     * @param anonymous 是否允许匿名访问
     * @param components OpenAPI 组件仓库，用于引用已注册 Schema
     */
    private void addResponses(
            String operationId,
            io.swagger.v3.oas.models.Operation operation,
            boolean anonymous,
            Components components) {
        ApiResponses responses = operation.getResponses() == null ? new ApiResponses() : operation.getResponses();
        operation.setResponses(responses);
        if (addOauthResponses(operationId, operation)) {
            return;
        }
        String successCode = operationId != null && ASYNC_ACCEPTED_OPERATION_IDS.contains(operationId) ? "202" : "200";
        ApiResponse success = responses.computeIfAbsent(successCode, ignored -> new ApiResponse().description("请求成功"));
        if (OP_ID_STREAM_NOTIFICATIONS.equals(operationId)) {
            ensureContent(
                    success, "text/event-stream", new StringSchema().description("SSE 事件流，event 类型为 notification"));
        } else {
            ensureSuccessContent(success, operationId, components);
        }
        if (OP_ID_AUTH_LOGIN.equals(operationId)) {
            documentLoginFlow(operation, success);
        }
        addErrorResponse(responses, "400", "请求参数格式错误、缺失或未通过业务校验");
        if (!anonymous) {
            addErrorResponse(responses, "401", "未认证、访问令牌无效或已过期（code=401 为未携带Token，code=40101 为Token已过期需重新登录）");
            addErrorResponse(responses, "403", "已认证，但没有访问该资源的权限");
        }
        if (OP_ID_DELETE_CURRENT_USER.equals(operationId)) {
            addErrorResponse(responses, "409", "会员权益尚未过期，或注销已重复提交/账号已失效");
        }
        addErrorResponse(responses, "429", "请求过于频繁，请稍后重试");
        addErrorResponse(responses, "500", "服务内部错误；请记录 traceId 后联系维护人员");
    }

    /**
     * 明确登录接口的三种成功状态及一次性票据的后续用途。
     *
     * @param operation 登录操作
     * @param success HTTP 200 响应
     */
    private void documentLoginFlow(io.swagger.v3.oas.models.Operation operation, ApiResponse success) {
        operation.setSummary("用户登录");
        operation.setDescription("统一认证入口，支持微信授权码、手机号短信验证码和手机号一键登录。"
                + "HTTP 200 表示认证流程处理成功，不一定代表已经登录；客户端必须根据 data.nextAction 继续："
                + "AUTHENTICATED 表示登录完成；REGISTER_REQUIRED 表示手机号未注册，"
                + "将 data.authTicket 作为 registrationTicket 调用 POST /api/v1/auth/register；"
                + "PHONE_BIND_REQUIRED 表示微信尚未确认手机号，将 data.authTicket 作为 wechatTicket 调用 "
                + "POST /api/v1/auth/wechat/bind-phone。票据有效期见 data.authTicketExpiresIn。"
                + "不要在 REGISTER_REQUIRED 和 PHONE_BIND_REQUIRED 之间混用票据。"
                + "登录请求提供 deviceId 时，后续请求必须传相同值。并发处理返回 1011 时应短暂退避并使用同一票据重试；"
                + "业务失败不会消费票据，成功结果可在 60 秒内幂等重放。");
        success.setDescription("认证流程处理成功；具体结果由 data.nextAction 决定");
        MediaType json = success.getContent().get("application/json");
        Map<String, Example> examples = new LinkedHashMap<>();
        examples.put(
                "authenticated",
                new Example()
                        .summary("已注册用户登录成功")
                        .value(successResponse(Map.of(
                                "nextAction", "AUTHENTICATED",
                                "accessToken", "eyJhbGciOiJIUzI1NiJ9.access-token",
                                "refreshToken", "eyJhbGciOiJIUzI1NiJ9.refresh-token",
                                "tokenType", "Bearer",
                                "expiresIn", 7200,
                                "isNewUser", false))));
        examples.put(
                "registerRequired",
                new Example()
                        .summary("手机号未注册，进入注册页")
                        .description("将 data.authTicket 原样传入注册接口的 registrationTicket 字段")
                        .value(successResponse(Map.of(
                                "nextAction", "REGISTER_REQUIRED",
                                "authTicket", "registration-ticket-example",
                                "authTicketExpiresIn", 300,
                                "accessToken", "temporary-access-token",
                                "tokenType", "Bearer",
                                "expiresIn", 300))));
        examples.put(
                "phoneBindRequired",
                new Example()
                        .summary("微信未确认手机号，进入手机号验证页")
                        .description("将 data.authTicket 原样传入微信绑定手机号接口的 wechatTicket 字段")
                        .value(successResponse(Map.of(
                                "nextAction", "PHONE_BIND_REQUIRED",
                                "authTicket", "wechat-bind-ticket-example",
                                "authTicketExpiresIn", 300))));
        json.setExamples(examples);
        json.setExample(null);
    }

    /**
     * 构造登录接口示例使用的统一成功响应，返回包含 code/msg/data/traceId 的标准响应结构。
     *
     * @param data 业务数据
     * @return 标准成功响应 Map
     */
    private Map<String, Object> successResponse(Map<String, Object> data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 0);
        response.put("msg", "成功");
        response.put("data", data);
        response.put("traceId", "01JAZ8Y7N2M4P6Q8R0STUVWX12");
        return response;
    }

    /**
     * 添加注销接口的成功响应示例。
     *
     * @param response 成功响应
     * @param operationId 操作唯一标识
     */
    private void addAccountDeletionSuccessExample(ApiResponse response, String operationId) {
        boolean isAccountDeletionOp =
                OP_ID_SEND_ACCOUNT_DELETION_CODE.equals(operationId) || OP_ID_DELETE_CURRENT_USER.equals(operationId);
        if (operationId == null || !isAccountDeletionOp) {
            return;
        }
        MediaType json = response.getContent().get("application/json");
        if (json == null) {
            return;
        }
        Map<String, Example> examples = new LinkedHashMap<>();
        if (OP_ID_SEND_ACCOUNT_DELETION_CODE.equals(operationId)) {
            examples.put(
                    "sent",
                    new Example()
                            .summary("已向账号绑定手机号发送验证码")
                            .description("手机号由服务端从当前登录账号读取，响应不回显手机号")
                            .value(successResponse(null)));
        } else {
            examples.put(
                    "accepted",
                    new Example()
                            .summary("账号已失效，清理任务已受理")
                            .value(successResponse(Map.of(
                                    "requestId", "account-deletion-request-01JAZ8Y7N2M4P6Q8R0STUVWX12",
                                    "jobId", "account-cleanup-job-7f3a",
                                    "status", "ACCEPTED"))));
        }
        json.setExamples(examples);
        json.setExample(null);
    }

    /**
     * 补齐成功响应，并将已有的具体业务模型放入统一响应的 data 字段。
     *
     * @param response 成功响应
     * @param operationId 操作唯一标识
     * @param components OpenAPI 组件容器
     */
    private void ensureSuccessContent(ApiResponse response, String operationId, Components components) {
        Content content = response.getContent() == null ? new Content() : response.getContent();
        response.setContent(content);
        MediaType json = content.get("application/json");
        Schema<?> dataSchema = json == null ? null : json.getSchema();
        if (dataSchema == null) {
            MediaType wildcard = content.get("*/*");
            dataSchema = wildcard == null ? null : wildcard.getSchema();
        }
        if (json == null) {
            json = new MediaType();
            content.addMediaType("application/json", json);
        }
        // RESPONSE_TYPES 中显式声明的业务模型优先级最高；
        // 即使 @RouterOperation 里已有裸 Result 或 ApiSuccessResponse 引用，也强制替换为带 data 具体类型的统一响应。
        Class<?> explicitResponseType = operationId == null ? null : RESPONSE_TYPES.get(operationId);
        if (explicitResponseType != null) {
            Schema<?> dataType = resolveSchema(explicitResponseType, components);
            if (LIST_RESPONSE_TYPES.contains(operationId)) {
                dataType = new ArraySchema().items(dataType);
            }
            json.setSchema(successEnvelope(dataType));
        } else if (OP_ID_UNIFIED_SEARCH.equals(operationId)) {
            ResolvedSchema resolved = ModelConverters.getInstance()
                    .resolveAsResolvedSchema(new AnnotatedType(
                                    new ParameterizedTypeReference<
                                            com.apex.scaffold.common.dto.PageResponse<SearchResultDTO>>() {}.getType())
                            .resolveAsRef(true));
            resolved.referencedSchemas.forEach(components::addSchemas);
            json.setSchema(successEnvelope(resolved.schema));
        } else if (OP_ID_REBUILD_SEARCH_INDEX.equals(operationId)) {
            json.setSchema(successEnvelope(new IntegerSchema().format("int64")));
        } else if (OP_ID_HANDLE_SSO_CALLBACK.equals(operationId)) {
            json.setSchema(successEnvelope(objectSchema(
                    Map.of(
                            "sessionId", new IntegerSchema().format("int64"),
                            "token", new StringSchema().description("SSO 会话标识，格式 SSO-TOKEN-{sessionId}，并非 JWT"),
                            "userInfo",
                                    new ObjectSchema()
                                            .additionalProperties(true)
                                            .description("协议适配器返回的用户信息"),
                            "expiryTime", resolveSchema(java.time.LocalDateTime.class, components)),
                    Set.of("sessionId", "token", "userInfo", "expiryTime"))));
        } else if (OP_ID_SYNC_KEYCLOAK_USER.equals(operationId)) {
            json.setSchema(successEnvelope(objectSchema(
                    Map.of(
                            "userId", new IntegerSchema().format("int64"),
                            "username", new StringSchema(),
                            "email", new StringSchema()),
                    Set.of("userId", "username", "email"))));
        } else if (OP_ID_DELETE_CURRENT_USER.equals(operationId)) {
            json.setSchema(successEnvelope(accountDeletionAcceptedSchema()));
        } else if (dataSchema == null) {
            json.setSchema(schemaReference(SUCCESS_SCHEMA));
        } else if (isGenericResultSchema(dataSchema)) {
            json.setSchema(schemaReference(SUCCESS_SCHEMA));
        } else if (!isSuccessEnvelopeSchema(dataSchema)) {
            json.setSchema(successEnvelope(dataSchema));
        }
        addAccountDeletionSuccessExample(response, operationId);
    }

    /**
     * 构造账号注销受理结果的 data Schema。
     *
     * <p>实现可以返回 requestId、jobId 或项目约定的等价异步清理标识；文档示例同时展示
     * requestId/jobId，便于客户端按任一稳定标识关联后续清理状态。</p>
     *
     * @return 异步清理受理结果 Schema
     */
    private ObjectSchema accountDeletionAcceptedSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.setDescription("异步清理受理结果；至少提供 requestId、jobId 或等价任务标识之一");
        schema.addProperty(
                "requestId",
                new StringSchema()
                        .description("注销请求标识，用于关联本次账号失效请求")
                        .example("account-deletion-request-01JAZ8Y7N2M4P6Q8R0STUVWX12"));
        schema.addProperty(
                "jobId", new StringSchema().description("异步清理任务标识，用于关联后台清理任务").example("account-cleanup-job-7f3a"));
        StringSchema status = new StringSchema();
        status.setDescription("任务受理状态");
        status.setEnum(List.of("ACCEPTED"));
        status.setExample("ACCEPTED");
        schema.addProperty("status", status);
        return schema;
    }

    /**
     * 判断 Schema 是否为缺少具体泛型信息的 {@code Result}。
     *
     * @param schema 待检查 Schema
     * @return 是裸 {@code Result} 时返回 {@code true}
     */
    private boolean isGenericResultSchema(Schema<?> schema) {
        String reference = schema.get$ref();
        return reference != null && reference.endsWith("/Result");
    }

    /** 判断 Schema 是否已经是增强后的统一成功响应。 */
    private boolean isSuccessEnvelopeSchema(Schema<?> schema) {
        String reference = schema.get$ref();
        return reference != null && reference.endsWith("/" + SUCCESS_SCHEMA);
    }

    /**
     * 使用具体业务模型构造统一成功响应。
     *
     * @param dataSchema data 字段模型
     * @return 完整成功响应模型
     */
    private ObjectSchema successEnvelope(Schema<?> dataSchema) {
        ObjectSchema schema = resultSchema("具体业务数据", false);
        schema.addProperty("data", dataSchema);
        return schema;
    }

    /**
     * 增加带统一 JSON 结构的错误响应。
     *
     * @param responses 响应集合
     * @param status HTTP 状态码
     * @param description 响应说明
     */
    private void addErrorResponse(ApiResponses responses, String status, String description) {
        ApiResponse response = responses.computeIfAbsent(status, ignored -> new ApiResponse().description(description));
        if (response.getDescription() == null || response.getDescription().isBlank()) {
            response.setDescription(description);
        }
        ensureContent(response, "application/json", schemaReference(ERROR_SCHEMA));
        MediaType json = response.getContent().get("application/json");
        if (isGenericResultSchema(json.getSchema())) {
            json.setSchema(schemaReference(ERROR_SCHEMA));
        }
    }

    /**
     * 确保响应声明指定媒体类型及 Schema。
     *
     * @param response 响应声明
     * @param contentType 媒体类型
     * @param schema 响应模型
     */
    private void ensureContent(ApiResponse response, String contentType, Schema<?> schema) {
        Content content = response.getContent() == null ? new Content() : response.getContent();
        response.setContent(content);
        MediaType mediaType = content.computeIfAbsent(contentType, ignored -> new MediaType());
        if (mediaType.getSchema() == null) {
            mediaType.setSchema(schema);
        }
    }

    /**
     * 使用 Swagger 模型转换器解析 DTO，并注册其引用模型。
     *
     * @param type DTO 类型
     * @param components OpenAPI 组件容器
     * @return DTO Schema
     */
    private Schema<?> resolveSchema(Class<?> type, Components components) {
        ResolvedSchema resolved =
                ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(type).resolveAsRef(true));
        resolved.referencedSchemas.forEach(components::addSchemas);
        return resolved.schema;
    }

    /**
     * 构造请求体声明。
     *
     * @param description 请求体说明
     * @param contentType 请求媒体类型
     * @param schema 请求 Schema
     * @return 请求体声明
     */
    private RequestBody requestBody(String description, String contentType, Schema<?> schema) {
        return new RequestBody()
                .required(true)
                .description(description)
                .content(new Content().addMediaType(contentType, new MediaType().schema(schema)));
    }

    /**
     * 为已有请求体补充说明和媒体类型说明。
     *
     * @param requestBody 请求体声明
     */
    private void ensureRequestBodyDescription(RequestBody requestBody) {
        if (requestBody.getDescription() == null || requestBody.getDescription().isBlank()) {
            requestBody.setDescription("请求参数；请按照对应 Content-Type 提交");
        }
    }

    /**
     * 判断操作是否已有同名同位置参数。
     *
     * @param operation OpenAPI 操作
     * @param name 参数名
     * @param in 参数位置
     * @return 已有参数，未找到时返回 {@code null}
     */
    private Parameter findParameter(io.swagger.v3.oas.models.Operation operation, String name, String in) {
        if (operation.getParameters() == null) {
            return null;
        }
        return operation.getParameters().stream()
                .filter(parameter -> name.equals(parameter.getName()) && in.equals(parameter.getIn()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 构造对象 Schema。
     *
     * @param properties 属性定义
     * @param required 必填属性
     * @return 对象 Schema
     */
    private ObjectSchema objectSchema(Map<String, Schema<?>> properties, Set<String> required) {
        ObjectSchema schema = new ObjectSchema();
        properties.forEach(schema::addProperty);
        schema.setRequired(new ArrayList<>(required));
        return schema;
    }

    /**
     * 构造带说明的必填字符串字段。
     *
     * @param description 字段说明
     * @return 字符串 Schema
     */
    private StringSchema requiredString(String description) {
        StringSchema schema = new StringSchema();
        schema.setDescription(description);
        schema.setMinLength(1);
        return schema;
    }

    /**
     * 构造组件引用 Schema。
     *
     * @param name 组件名称
     * @return 引用 Schema
     */
    private Schema<?> schemaReference(String name) {
        return new Schema<>().$ref("#/components/schemas/" + name);
    }

    /**
     * 将常见参数名转换为中文说明。
     *
     * @param name 参数名
     * @return 参数名对应的中文说明；未收录的参数名原样返回
     */
    private String humanizeParameter(String name) {
        return switch (name) {
            case "userId" -> "用户 ID";
            case "roleId" -> "角色 ID";
            case "id" -> "资源 ID";
            default -> name;
        };
    }

    /**
     * 创建查询参数目录。
     *
     * @return 操作 ID 到查询参数列表的映射
     */
    private static Map<String, List<QueryParameter>> queryParameters() {
        Map<String, List<QueryParameter>> parameters = new LinkedHashMap<>();
        parameters.put(
                "adminListUsers",
                List.of(
                        integerQuery("page", "页码", false, 1, 1, null),
                        integerQuery("size", "每页数量", false, 30, 1, 200),
                        stringQuery("keyword", "检索关键字（昵称/手机号等）", false, null, List.of()),
                        stringQuery("status", "用户状态过滤", false, null, List.of())));
        return Map.copyOf(parameters);
    }

    /**
     * 创建字符串查询参数定义，用于声明 API 接口的字符串类型 Query String 参数。
     *
     * @param name         参数名
     * @param description  参数描述
     * @param required     是否必填
     * @param defaultValue 默认值（可选）
     * @param values       枚举值列表（可选）
     * @return 查询参数定义
     */
    private static QueryParameter stringQuery(
            String name, String description, boolean required, String defaultValue, List<String> values) {
        StringSchema schema = new StringSchema();
        schema.setDescription(description);
        if (defaultValue != null) {
            schema.setDefault(defaultValue);
        }
        if (!values.isEmpty()) {
            schema.setEnum(values);
        }
        return new QueryParameter(name, description, required, schema);
    }

    /**
     * 创建整数查询参数定义，支持 int32 格式和可选的最小/最大值约束。
     *
     * @param name         参数名
     * @param description  参数描述
     * @param required     是否必填
     * @param defaultValue 默认值（可选）
     * @param minimum      最小值（可选）
     * @param maximum      最大值（可选）
     * @return 查询参数定义
     */
    private static QueryParameter integerQuery(
            String name, String description, boolean required, Integer defaultValue, Integer minimum, Integer maximum) {
        IntegerSchema schema = new IntegerSchema();
        schema.setFormat("int32");
        schema.setDescription(description);
        if (defaultValue != null) {
            schema.setDefault(defaultValue);
        }
        if (minimum != null) {
            schema.setMinimum(java.math.BigDecimal.valueOf(minimum));
        }
        if (maximum != null) {
            schema.setMaximum(java.math.BigDecimal.valueOf(maximum));
        }
        return new QueryParameter(name, description, required, schema);
    }

    /**
     * 创建长整数查询参数定义，使用 int64 格式，最小值为 1。
     *
     * @param name        参数名
     * @param description 参数描述
     * @param required    是否必填
     * @return 查询参数定义
     */
    private static QueryParameter integer64Query(String name, String description, boolean required) {
        return new QueryParameter(
                name, description, required, new IntegerSchema().format("int64").minimum(java.math.BigDecimal.ONE));
    }

    /**
     * 查询参数定义，用于在 OpenAPI 文档中声明 Query String 参数的名称、类型和约束。
     *
     * @param name 查询参数名
     * @param description 参数说明
     * @param required 是否必填
     * @param schema 参数的类型声明
     */
    private record QueryParameter(String name, String description, boolean required, Schema<?> schema) {

        /**
         * 转换为 OpenAPI 参数。
         *
         * @return OpenAPI 查询参数
         */
        private Parameter toOpenApiParameter() {
            return new Parameter()
                    .name(name)
                    .in("query")
                    .required(required)
                    .description(description)
                    .schema(schema);
        }
    }

    /**
     * 请求头参数定义，用于在 OpenAPI 文档中声明自定义 HTTP Header 参数。
     *
     * @param name 请求头名称
     * @param description 参数说明
     * @param required 是否必填
     */
    private record HeaderParameter(String name, String description, boolean required) {

        /** 转换为 OpenAPI 参数。 */
        private Parameter toOpenApiParameter() {
            return new Parameter()
                    .name(name)
                    .in("header")
                    .required(required)
                    .description(description)
                    .schema(new StringSchema().minLength(1));
        }
    }

    /**
     * 判断已有请求体是否包含有效的 Schema。
     *
     * <p>SpringDoc 在 WebFlux 函数式路由模式下可能为 POST 端点自动生成
     * 空的 {@code RequestBody}（content 非空但各 MediaType 的 schema 均为 null），
     * 此时应回退到 {@link #JSON_REQUEST_TYPES} 等手工映射，避免在线文档
     * 出现"请求体存在但无字段"的问题。</p>
     *
     * @param requestBody SpringDoc 自动生成的请求体声明
     * @return 请求体内容中至少有一个 MediaType 包含非空 Schema 时返回 {@code true}
     */
    private boolean hasValidRequestBodySchema(RequestBody requestBody) {
        if (requestBody == null) {
            return false;
        }
        if (requestBody.getContent() == null || requestBody.getContent().isEmpty()) {
            return false;
        }
        return requestBody.getContent().values().stream()
                .anyMatch(mediaType -> mediaType != null && mediaType.getSchema() != null);
    }
}
