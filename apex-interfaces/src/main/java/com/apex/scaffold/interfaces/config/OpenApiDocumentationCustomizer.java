package com.apex.scaffold.interfaces.config;

import com.apex.scaffold.application.dto.AccountDeletionRequest;
import com.apex.scaffold.application.dto.FileUploadResult;
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
            Map.entry("adminCreateDept", com.apex.scaffold.domain.model.entity.DeptEntity.class),
            Map.entry("adminUpdateDept", com.apex.scaffold.domain.model.entity.DeptEntity.class));

    /** 操作对应的查询参数定义，key 为 operationId，value 为该操作支持的查询参数列表。 */
    private static final Map<String, List<QueryParameter>> QUERY_PARAMETERS = queryParameters();

    /** 操作对应的 multipart/form-data 表单字段定义。 */
    private static final Map<String, List<FormField>> MULTIPART_REQUEST_FIELDS = multipartFields();

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
            // 文件上传
            Map.entry("fileUpload", FileUploadResult.class),
            Map.entry("uploadGenericFile", FileUploadResult.class),
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
            Map.entry("adminDeptTree", DeptDTO.class),
            Map.entry("adminDeptDetail", DeptDTO.class),
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
            "adminDeptTree");

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
                    path, operation.getOperationId(), operation, components, publicPath.test(path));
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
                "code", new IntegerSchema().format("int32").description("业务状态码").example(error ? 400 : 200));
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
        for (QueryParameter definition : QUERY_PARAMETERS.getOrDefault(operationId, List.of())) {
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
        List<FormField> formFields = MULTIPART_REQUEST_FIELDS.get(operationId);
        if (formFields != null) {
            operation.setRequestBody(multipartRequestBody(formFields));
            return;
        }
        if ("validateMobile".equals(operationId)) {
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
     */
    private void addResponses(
            String operationId,
            io.swagger.v3.oas.models.Operation operation,
            boolean anonymous,
            Components components) {
        ApiResponses responses = operation.getResponses() == null ? new ApiResponses() : operation.getResponses();
        operation.setResponses(responses);
        String successCode = operationId != null && ASYNC_ACCEPTED_OPERATION_IDS.contains(operationId) ? "202" : "200";
        ApiResponse success = responses.computeIfAbsent(successCode, ignored -> new ApiResponse().description("请求成功"));
        if ("getLocalFileContent".equals(operationId)) {
            ensureContent(success, "application/octet-stream", new StringSchema().format("binary"));
        } else {
            ensureSuccessContent(success, operationId, components);
        }
        if ("authLogin".equals(operationId)) {
            documentLoginFlow(operation, success);
        }
        addErrorResponse(responses, "400", "请求参数格式错误、缺失或未通过业务校验");
        if (!anonymous) {
            addErrorResponse(responses, "401", "未认证、访问令牌无效或已过期（code=401 为未携带Token，code=40101 为Token已过期需重新登录）");
            addErrorResponse(responses, "403", "已认证，但没有访问该资源的权限");
        }
        if ("deleteCurrentUser".equals(operationId)) {
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
        if (operationId == null
                || !("sendAccountDeletionCode".equals(operationId) || "deleteCurrentUser".equals(operationId))) {
            return;
        }
        MediaType json = response.getContent().get("application/json");
        if (json == null) {
            return;
        }
        Map<String, Example> examples = new LinkedHashMap<>();
        if ("sendAccountDeletionCode".equals(operationId)) {
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
        } else if ("deleteCurrentUser".equals(operationId)) {
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
                "authRefresh",
                List.of(
                        stringQuery("refreshToken", "刷新令牌", true, null, List.of()),
                        stringQuery("deviceId", "设备标识，用于校验令牌绑定设备", false, null, List.of())));
        parameters.put("fileUpload", List.of(stringQuery("sex", "照片人物性别", false, null, List.of("male", "female"))));
        List<QueryParameter> storage = List.of(
                stringQuery("bucketName", "存储桶名称", true, null, List.of()),
                stringQuery("fileName", "文件名称或对象键", true, null, List.of()));
        parameters.put("getFileUrl", storage);
        parameters.put("deleteFile", storage);
        parameters.put("fileExists", storage);
        parameters.put("getLocalFileContent", List.of(stringQuery("fileName", "本地文件名称", true, null, List.of())));
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

    /** 查询参数定义，用于在 OpenAPI 文档中声明 Query String 参数的名称、类型和约束。 */
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

    /** 请求头参数定义，用于在 OpenAPI 文档中声明自定义 HTTP Header 参数。 */
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

    /** multipart/form-data 表单字段定义。 */
    private record FormField(String name, String type, String description, boolean required) {}

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

    /**
     * 构造 multipart/form-data 请求体。
     *
     * @param fields 表单字段列表
     * @return 请求体声明
     */
    private RequestBody multipartRequestBody(List<FormField> fields) {
        ObjectSchema schema = new ObjectSchema();
        for (FormField field : fields) {
            Schema<?> fieldSchema =
                    switch (field.type()) {
                        case "file" -> new io.swagger.v3.oas.models.media.FileSchema();
                        case "array-file" -> {
                            ArraySchema arr = new ArraySchema();
                            arr.setItems(new io.swagger.v3.oas.models.media.FileSchema());
                            yield arr;
                        }
                        default -> new StringSchema().description(field.description());
                    };
            fieldSchema.setDescription(field.description());
            schema.addProperty(field.name(), fieldSchema);
        }
        schema.setRequired(
                fields.stream().filter(FormField::required).map(FormField::name).toList());
        return new RequestBody()
                .required(true)
                .description("multipart/form-data 表单请求参数")
                .content(new Content().addMediaType("multipart/form-data", new MediaType().schema(schema)));
    }

    /**
     * 创建 multipart 表单字段映射。
     *
     * @return 操作 ID 到表单字段列表的映射
     */
    private static Map<String, List<FormField>> multipartFields() {
        Map<String, List<FormField>> fields = new LinkedHashMap<>();
        // ---- 文件上传接口 ----
        fields.put(
                "fileUpload",
                List.of(
                        fileField("file", "上传的文件"),
                        stringField("sex", "性别（0=男性/1=女性）", true),
                        stringField("usage", "用途分类（默认: GENERIC）", false),
                        stringField("clientRequestId", "客户端幂等键", false)));
        return Map.copyOf(fields);
    }

    /**
     * 创建文件字段定义，用于 multipart/form-data 请求中的单文件字段。
     *
     * @param name        字段名
     * @param description 字段描述
     * @return FormField 定义
     */
    private static FormField fileField(String name, String description) {
        return new FormField(name, "file", description, true);
    }

    /**
     * 创建多文件字段定义，用于 multipart/form-data 请求中可上传多个文件的字段。
     *
     * @param name        字段名
     * @param description 字段描述
     * @return FormField 定义
     */
    private static FormField arrayFileField(String name, String description) {
        return new FormField(name, "array-file", description, true);
    }

    /**
     * 创建字符串表单字段定义，用于 multipart/form-data 请求中的文本字段。
     *
     * @param name        字段名
     * @param description 字段描述
     * @param required    是否必填
     * @return FormField 定义
     */
    private static FormField stringField(String name, String description, boolean required) {
        return new FormField(name, "string", description, required);
    }
}
