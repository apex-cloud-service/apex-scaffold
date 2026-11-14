package ${ifacePackage}.handler.${module};

import ${appPackage}.dto.${module}.${entityName}Request;
import ${appPackage}.service.${entityName}Service;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.interfaces.handler.BaseHandler;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
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
 * <p>遵循 Handler 统一规范（v2.2.0 起）：
 * 成功出口统一使用 {@link BaseHandler#buildSuccess(Object)}，
 * 请求体解析与校验统一使用 {@link BaseHandler#parseBody(ServerRequest, Class)}，
 * 路径参数校验统一使用 {@link BaseHandler#requirePositiveId(ServerRequest)}。
 * 生成产物不再包含私有 {@code ok()} / {@code id()} 样板方法。</p>
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
 * @see BaseHandler
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
        return ${entityVar}Service.list().flatMap(this::buildSuccess);
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
        return ${entityVar}Service.get(requirePositiveId(request)).flatMap(this::buildSuccess);
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
        return parseBody(request, ${entityName}Request.class)
                .flatMap(${entityVar}Service::create)
                .flatMap(this::buildSuccess);
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
        long id = requirePositiveId(request);
        return parseBody(request, ${entityName}Request.class)
                .flatMap(body -> ${entityVar}Service.update(id, body))
                .flatMap(this::buildSuccess);
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
                .delete(requirePositiveId(request))
                .flatMap(deleted -> buildSuccess(java.util.Map.of("deleted", deleted)));
    }
}
