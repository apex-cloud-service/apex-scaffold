package com.apex.example.handler;

import static org.springframework.web.reactive.function.server.ServerResponse.ok;

import com.apex.example.dto.CreateUserRequest;
import com.apex.example.dto.UserResponse;
import com.apex.example.service.ExampleUserService;
import com.apex.scaffold.common.annotation.FeatureGate;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.interfaces.handler.BaseHandler;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 示例用户处理器
 *
 * <p>展示如何编写 WebFlux 函数式端点:
 * <ul>
 *   <li>使用 {@link ServerRequest}/{@link ServerResponse}</li>
 *   <li>集成 Swagger 文档注解</li>
 *   <li>统一响应格式封装（{@link Result}）</li>
 *   <li>继承 {@link BaseHandler} 获得请求体校验（{@link #validateAndThrow}）</li>
 *   <li>分页查询参数解析（query 参数 pageNum/pageSize）</li>
 *   <li>商业版功能授权（{@link FeatureGate}）</li>
 * </ul>
 *
 * <p>异常处理约定: 处理器内不捕获异常,统一由
 * {@link com.apex.scaffold.interfaces.exception.GlobalExceptionHandler}
 * 转换为标准 {@link Result} 错误响应。
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@Slf4j
@Component
@Tag(name = "示例模块-用户管理", description = "展示脚手架的使用方法")
public class ExampleUserHandler extends BaseHandler {

    /** 创建用户结果的统一响应结构，仅用于接口文档。 */
    @Schema(name = "ExampleUserResult")
    public static class ExampleUserCreateResult extends Result<UserResponse> {}

    /** 用户分页的统一响应结构，仅用于接口文档。 */
    @Schema(name = "ExampleUserPageResult")
    public static class ExampleUserPageResult extends Result<com.apex.scaffold.common.dto.PageResponse<UserResponse>> {}

    /** 字符串结果的统一响应结构，仅用于接口文档。 */
    @Schema(name = "ExampleStringResult")
    public static class ExampleStringResult extends Result<String> {}

    /** 报表结果的统一响应结构，仅用于接口文档。 */
    @Schema(name = "ExampleReportResult")
    public static class ExampleReportResult extends Result<Map<String, Object>> {}

    /** 分页参数默认值：页码。 */
    private static final int DEFAULT_PAGE_NUM = 1;

    /** 分页参数默认值：每页大小。 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 分页参数每页大小上限。 */
    private static final int MAX_PAGE_SIZE = 100;

    private final ExampleUserService userService;

    /**
     * 构造方法，注入消息工具、校验器与用户服务。
     *
     * @param messageUtils 国际化消息工具
     * @param validator    Jakarta Bean Validation 校验器
     * @param userService  示例用户服务
     */
    public ExampleUserHandler(MessageUtils messageUtils, Validator validator, ExampleUserService userService) {
        super(messageUtils, validator);
        this.userService = userService;
    }

    /**
     * 创建用户
     *
     * @param request HTTP请求
     * @return 响应结果
     */
    @Operation(
            operationId = "exampleCreateUser",
            summary = "创建用户",
            description = "示例接口：演示 Handler + RouterFunction 的标准写法，含请求体校验、统一响应与审计注解")
    @RequestBody(
            required = true,
            description = "创建用户请求",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateUserRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "创建成功，data 为创建后的用户",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ExampleUserCreateResult.class))),
        @ApiResponse(responseCode = "400", description = "请求体缺失或字段校验失败")
    })
    @OperationLog(
            module = "ExampleUser",
            type = OperationLog.OperationType.INSERT,
            description = "createUser",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> createUser(ServerRequest request) {
        return request.bodyToMono(CreateUserRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(this::validateAndThrow)
                .flatMap(userService::createUser)
                .flatMap(response -> ok().bodyValue(Result.success(response)));
    }

    /**
     * 查询用户详情
     *
     * @param request HTTP请求
     * @return 响应结果
     */
    @Operation(operationId = "exampleGetUser", summary = "查询用户", description = "示例接口：根据路径参数 ID 查询用户详情")
    @Parameter(
            name = "id",
            in = ParameterIn.PATH,
            required = true,
            description = "用户 ID",
            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为用户详情",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ExampleUserCreateResult.class))),
        @ApiResponse(responseCode = "400", description = "用户 ID 格式非法"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    @OperationLog(
            module = "ExampleUser",
            type = OperationLog.OperationType.SELECT,
            description = "getUser",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getUser(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));

        return userService.getUserById(id).flatMap(response -> ok().bodyValue(Result.success(response)));
    }

    /**
     * 分页查询用户列表
     *
     * <p>从 query 参数读取 {@code pageNum}（默认 1）与 {@code pageSize}（默认 10，上限 100），
     * 返回脚手架统一分页模型 {@code PageResponse}。</p>
     *
     * @param request HTTP请求
     * @return 分页响应结果
     */
    @Operation(
            operationId = "exampleListUsers",
            summary = "分页查询用户列表",
            description = "示例接口：支持 pageNum/pageSize 分页参数，返回统一分页模型 PageResponse；pageSize 上限 100")
    @Parameter(
            name = "pageNum",
            in = ParameterIn.QUERY,
            description = "页码，从 1 开始，缺省 1",
            schema = @Schema(type = "integer", format = "int64", defaultValue = "1", minimum = "1"))
    @Parameter(
            name = "pageSize",
            in = ParameterIn.QUERY,
            description = "每页数量，缺省 10，上限 100",
            schema = @Schema(type = "integer", format = "int64", defaultValue = "10", minimum = "1", maximum = "100"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为用户分页结果",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ExampleUserPageResult.class))),
        @ApiResponse(responseCode = "400", description = "分页参数非法（小于 1 或超过上限）")
    })
    @OperationLog(
            module = "ExampleUser",
            type = OperationLog.OperationType.SELECT,
            description = "listUsers",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> listUsers(ServerRequest request) {
        long pageNum = request.queryParam("pageNum").map(Long::parseLong).orElse((long) DEFAULT_PAGE_NUM);
        long pageSize = request.queryParam("pageSize").map(Long::parseLong).orElse((long) DEFAULT_PAGE_SIZE);

        // 参数兜底：非法分页参数直接拒绝（避免负数/超大分页）
        if (pageNum < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "分页参数非法"));
        }

        return userService.listUsers(pageNum, pageSize).flatMap(response -> ok().bodyValue(Result.success(response)));
    }

    /**
     * 更新用户
     *
     * @param request HTTP请求
     * @return 响应结果
     */
    @Operation(operationId = "exampleUpdateUser", summary = "更新用户", description = "示例接口：根据路径参数 ID 整体更新用户信息")
    @Parameter(
            name = "id",
            in = ParameterIn.PATH,
            required = true,
            description = "用户 ID",
            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
    @RequestBody(
            required = true,
            description = "更新用户请求",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateUserRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "更新成功，data 为更新后的用户",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ExampleUserCreateResult.class))),
        @ApiResponse(responseCode = "400", description = "用户 ID 格式非法、请求体缺失或字段校验失败"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    @OperationLog(
            module = "ExampleUser",
            type = OperationLog.OperationType.UPDATE,
            description = "updateUser",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> updateUser(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));

        return request.bodyToMono(CreateUserRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(this::validateAndThrow)
                .flatMap(dto -> userService.updateUser(id, dto))
                .flatMap(response -> ok().bodyValue(Result.success(response)));
    }

    /**
     * 删除用户
     *
     * @param request HTTP请求
     * @return 响应结果
     */
    @Operation(operationId = "exampleDeleteUser", summary = "删除用户", description = "示例接口：根据路径参数 ID 逻辑删除用户（保留记录，仅置删除标记）")
    @Parameter(
            name = "id",
            in = ParameterIn.PATH,
            required = true,
            description = "用户 ID",
            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "删除成功，data 为结果提示文本",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ExampleStringResult.class))),
        @ApiResponse(responseCode = "400", description = "用户 ID 格式非法"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    @OperationLog(
            module = "ExampleUser",
            type = OperationLog.OperationType.DELETE,
            description = "deleteUser",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> deleteUser(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));

        return userService.deleteUser(id).then(ok().bodyValue(Result.success("删除成功")));
    }

    /**
     * 高级运营报表(商业版功能)
     *
     * <p>{@code @FeatureGate}示例接口: 该接口需要License中授权了
     * {@code premium_report}功能才能访问(标准版/企业版)。
     * 未授权时由{@link com.apex.scaffold.license.FeatureGateAspect}
     * 返回权限不足的错误响应。
     *
     * @param request HTTP请求
     * @return 响应结果
     */
    @Operation(
            operationId = "examplePremiumReport",
            summary = "高级运营报表（商业版）",
            description = "示例接口：@FeatureGate 用法示范，需标准版/企业版 License 授权 premium_report 功能；"
                    + "未授权时由 FeatureGateAspect 返回权限不足错误")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "生成成功，data 为报表字段",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ExampleReportResult.class))),
        @ApiResponse(responseCode = "403", description = "License 未授权 premium_report 功能")
    })
    @FeatureGate(value = "premium_report", message = "高级运营报表需要标准版或企业版License,请升级后重试")
    @OperationLog(
            module = "ExampleUser",
            type = OperationLog.OperationType.OTHER,
            description = "premiumReport",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> premiumReport(ServerRequest request) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportId", "RPT-" + LocalDate.now() + "-001");
        report.put("title", "月度运营报表");
        report.put("generatedAt", LocalDate.now().toString());
        report.put("totalUsers", 1280);
        report.put("activeUsers", 356);
        return ok().bodyValue(Result.success(report));
    }
}
