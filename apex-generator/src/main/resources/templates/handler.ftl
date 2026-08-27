package ${ifacePackage}.handler.${module};

import ${appPackage}.dto.${module}.${entityName}Request;
import ${appPackage}.service.${entityName}Service;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.interfaces.handler.BaseHandler;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * ${tableComment}处理器（函数式端点）。
 *
 * <p>提供${tableComment}的查询、创建、更新与删除 API，所有端点要求
 * {@code ${module}:${entityVar}:*} 权限。</p>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /admin-api/v1/${module}/${entityVar}s - 查询${tableComment}列表</li>
 *   <li>GET /admin-api/v1/${module}/${entityVar}s/{id} - 查询${tableComment}详情</li>
 *   <li>POST /admin-api/v1/${module}/${entityVar}s - 创建${tableComment}</li>
 *   <li>PUT /admin-api/v1/${module}/${entityVar}s/{id} - 更新${tableComment}</li>
 *   <li>DELETE /admin-api/v1/${module}/${entityVar}s/{id} - 删除${tableComment}</li>
 * </ul>
 *
 * @author ${author}
 * @version 1.0.0
 * @since 1.0.0
 * @see ${entityName}Service
 */
@Component
@Tag(name = "后台-${tableComment}", description = "${tableComment}管理")
public class ${entityName}Handler extends BaseHandler {

    /**
     * ${tableComment}应用服务。
     */
    private final ${entityName}Service ${entityVar}Service;

    /**
     * 创建${tableComment}处理器。
     *
     * @param ${entityVar}Service ${tableComment}应用服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public ${entityName}Handler(${entityName}Service ${entityVar}Service, MessageUtils messageUtils, Validator validator) {
        super(messageUtils, validator);
        this.${entityVar}Service = ${entityVar}Service;
    }

    /**
     * 查询${tableComment}列表。
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为${tableComment} DTO 列表
     */
    @RequirePermission("${module}:${entityVar}:list")
    @Operation(operationId = "adminList${entityName}s", summary = "查询${tableComment}列表")
    public Mono<ServerResponse> list(ServerRequest request) {
        return ${entityVar}Service.list().flatMap(this::ok);
    }

    /**
     * 查询${tableComment}详情。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为${tableComment}实体
     * @throws BusinessException 当 {@code id} 非法时抛出
     */
    @RequirePermission("${module}:${entityVar}:query")
    @Operation(operationId = "adminGet${entityName}", summary = "查询${tableComment}详情")
    public Mono<ServerResponse> detail(ServerRequest request) {
        return ${entityVar}Service.get(id(request)).flatMap(this::ok);
    }

    /**
     * 创建${tableComment}。
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为已创建的${tableComment}实体
     * @throws BusinessException 当请求体为空时抛出
     */
    @RequirePermission("${module}:${entityVar}:add")
    @Operation(operationId = "adminCreate${entityName}", summary = "创建${tableComment}")
    @OperationLog(module = "${tableComment}", type = OperationLog.OperationType.INSERT, description = "创建${tableComment}")
    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(${entityName}Request.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(this::validateAndThrow)
                .flatMap(${entityVar}Service::create)
                .flatMap(this::ok);
    }

    /**
     * 更新${tableComment}。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为已更新的${tableComment}实体
     * @throws BusinessException 当 {@code id} 非法或请求体为空时抛出
     */
    @RequirePermission("${module}:${entityVar}:edit")
    @Operation(operationId = "adminUpdate${entityName}", summary = "更新${tableComment}")
    @OperationLog(module = "${tableComment}", type = OperationLog.OperationType.UPDATE, description = "更新${tableComment}")
    public Mono<ServerResponse> update(ServerRequest request) {
        Long id = id(request);
        return request.bodyToMono(${entityName}Request.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(this::validateAndThrow)
                .flatMap(body -> ${entityVar}Service.update(id, body))
                .flatMap(this::ok);
    }

    /**
     * 删除${tableComment}。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为 {@code {"deleted": 是否删除成功}}
     * @throws BusinessException 当 {@code id} 非法时抛出
     */
    @RequirePermission("${module}:${entityVar}:remove")
    @Operation(operationId = "adminDelete${entityName}", summary = "删除${tableComment}")
    @OperationLog(module = "${tableComment}", type = OperationLog.OperationType.DELETE, description = "删除${tableComment}")
    public Mono<ServerResponse> delete(ServerRequest request) {
        return ${entityVar}Service
                .delete(id(request))
                .flatMap(deleted -> ok(java.util.Map.of("deleted", deleted)));
    }

    /**
     * 解析并校验路径参数 {@code id}。
     *
     * @param request HTTP 请求
     * @return 大于 0 的${tableComment} ID
     * @throws BusinessException 当参数缺失、非数字或不大于 0 时抛出
     */
    private Long id(ServerRequest request) {
        Long id = safeParseLong(request.pathVariable("id"));
        if (id == null || id <= 0) throw new BusinessException(ResultCode.PARAM_ERROR, "id格式错误");
        return id;
    }

    /**
     * 构造统一 JSON 成功响应。
     *
     * @param data 业务数据，可为 {@code null}
     * @return 携带当前 traceId 的 200 JSON 响应
     */
    private <T> Mono<ServerResponse> ok(T data) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Result.success(data, TraceIdUtil.getTraceId()));
    }
}
