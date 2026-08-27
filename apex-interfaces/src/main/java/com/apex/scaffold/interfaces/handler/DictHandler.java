package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.DictDataRequest;
import com.apex.scaffold.application.dto.admin.DictTypeRequest;
import com.apex.scaffold.application.service.DictService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
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
 * 系统字典处理器（函数式端点）。
 *
 * <p>提供字典类型与字典数据的查询、创建、更新、删除与启停管理 API，
 * 所有端点要求 {@code system:dict:*} 权限。字典采用"类型-数据"
 * 两级模型，是管理系统下拉选项与枚举翻译能力的基础。</p>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /admin-api/v1/dict/types - 查询字典类型列表</li>
 *   <li>GET /admin-api/v1/dict/types/{id} - 查询字典类型详情</li>
 *   <li>POST /admin-api/v1/dict/types - 创建字典类型</li>
 *   <li>PUT /admin-api/v1/dict/types/{id} - 更新字典类型</li>
 *   <li>DELETE /admin-api/v1/dict/types/{id} - 删除字典类型</li>
 *   <li>PUT /admin-api/v1/dict/types/{id}/status - 启用/停用字典类型</li>
 *   <li>GET /admin-api/v1/dict/data?dictType=xx - 按类型查询数据项</li>
 *   <li>GET /admin-api/v1/dict/data/{id} - 查询字典数据详情</li>
 *   <li>POST /admin-api/v1/dict/data - 创建字典数据</li>
 *   <li>PUT /admin-api/v1/dict/data/{id} - 更新字典数据</li>
 *   <li>DELETE /admin-api/v1/dict/data/{id} - 删除字典数据</li>
 *   <li>PUT /admin-api/v1/dict/data/{id}/status - 启用/停用字典数据</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see DictService
 */
@Component
@Tag(name = "后台-字典管理", description = "系统字典类型与数据管理")
public class DictHandler extends BaseHandler {

    /**
     * 字典管理应用服务。
     */
    private final DictService dictService;

    /**
     * 创建系统字典处理器。
     *
     * @param dictService 字典管理应用服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public DictHandler(DictService dictService, MessageUtils messageUtils, Validator validator) {
        super(messageUtils, validator);
        this.dictService = dictService;
    }

    // ==================== 字典类型 ====================

    /**
     * 查询字典类型列表。
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为字典类型 DTO 列表
     */
    @RequirePermission("system:dict:list")
    @Operation(operationId = "adminListDictTypes", summary = "查询字典类型列表")
    public Mono<ServerResponse> listTypes(ServerRequest request) {
        return dictService.listTypes().flatMap(this::ok);
    }

    /**
     * 查询字典类型详情。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为字典类型实体
     * @throws BusinessException 当 {@code id} 非法时抛出
     */
    @RequirePermission("system:dict:query")
    @Operation(operationId = "adminGetDictType", summary = "查询字典类型详情")
    public Mono<ServerResponse> getType(ServerRequest request) {
        return dictService.getType(typeId(request)).flatMap(this::ok);
    }

    /**
     * 创建字典类型。
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为已创建的字典类型实体
     * @throws BusinessException 当请求体为空时抛出
     */
    @RequirePermission("system:dict:add")
    @Operation(operationId = "adminCreateDictType", summary = "创建字典类型")
    @OperationLog(module = "字典管理", type = OperationLog.OperationType.INSERT, description = "创建字典类型")
    public Mono<ServerResponse> createType(ServerRequest request) {
        return request.bodyToMono(DictTypeRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(this::validateAndThrow)
                .flatMap(dictService::createType)
                .flatMap(this::ok);
    }

    /**
     * 更新字典类型。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为已更新的字典类型实体
     * @throws BusinessException 当 {@code id} 非法或请求体为空时抛出
     */
    @RequirePermission("system:dict:edit")
    @Operation(operationId = "adminUpdateDictType", summary = "更新字典类型")
    @OperationLog(module = "字典管理", type = OperationLog.OperationType.UPDATE, description = "更新字典类型")
    public Mono<ServerResponse> updateType(ServerRequest request) {
        Long id = typeId(request);
        return request.bodyToMono(DictTypeRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(this::validateAndThrow)
                .flatMap(body -> dictService.updateType(id, body))
                .flatMap(this::ok);
    }

    /**
     * 删除字典类型。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为 {@code {"deleted": 是否删除成功}}
     * @throws BusinessException 当 {@code id} 非法时抛出
     */
    @RequirePermission("system:dict:remove")
    @Operation(operationId = "adminDeleteDictType", summary = "删除字典类型")
    @OperationLog(module = "字典管理", type = OperationLog.OperationType.DELETE, description = "删除字典类型")
    public Mono<ServerResponse> deleteType(ServerRequest request) {
        return dictService.deleteType(typeId(request)).flatMap(deleted -> ok(java.util.Map.of("deleted", deleted)));
    }

    /**
     * 启用或停用字典类型。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为更新后的字典类型实体
     * @throws BusinessException 当 {@code id} 非法或请求体为空时抛出
     */
    @RequirePermission("system:dict:edit")
    @Operation(operationId = "adminUpdateDictTypeStatus", summary = "启用或停用字典类型")
    @OperationLog(module = "字典管理", type = OperationLog.OperationType.UPDATE, description = "更新字典类型状态")
    public Mono<ServerResponse> updateTypeStatus(ServerRequest request) {
        Long id = typeId(request);
        return request.bodyToMono(StatusRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(this::validateAndThrow)
                .flatMap(body -> dictService.updateTypeStatus(id, body.isEnabled()))
                .flatMap(this::ok);
    }

    // ==================== 字典数据 ====================

    /**
     * 按字典类型编码查询数据项。
     *
     * @param request HTTP 请求，查询参数 {@code dictType} 必填
     * @return 带 traceId 的统一成功响应，data 为字典数据 DTO 列表
     * @throws BusinessException 当 {@code dictType} 缺失时抛出
     */
    @RequirePermission("system:dict:list")
    @Operation(operationId = "adminListDictData", summary = "按类型查询字典数据")
    public Mono<ServerResponse> listData(ServerRequest request) {
        String dictType = request.queryParam("dictType").orElse(null);
        if (dictType == null || dictType.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "dictType不能为空"));
        }
        return dictService.listDataByType(dictType).flatMap(this::ok);
    }

    /**
     * 查询字典数据详情。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为字典数据实体
     * @throws BusinessException 当 {@code id} 非法时抛出
     */
    @RequirePermission("system:dict:query")
    @Operation(operationId = "adminGetDictData", summary = "查询字典数据详情")
    public Mono<ServerResponse> getData(ServerRequest request) {
        return dictService.getData(dataId(request)).flatMap(this::ok);
    }

    /**
     * 创建字典数据。
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为已创建的字典数据实体
     * @throws BusinessException 当请求体为空时抛出
     */
    @RequirePermission("system:dict:add")
    @Operation(operationId = "adminCreateDictData", summary = "创建字典数据")
    @OperationLog(module = "字典管理", type = OperationLog.OperationType.INSERT, description = "创建字典数据")
    public Mono<ServerResponse> createData(ServerRequest request) {
        return request.bodyToMono(DictDataRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(this::validateAndThrow)
                .flatMap(dictService::createData)
                .flatMap(this::ok);
    }

    /**
     * 更新字典数据。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为已更新的字典数据实体
     * @throws BusinessException 当 {@code id} 非法或请求体为空时抛出
     */
    @RequirePermission("system:dict:edit")
    @Operation(operationId = "adminUpdateDictData", summary = "更新字典数据")
    @OperationLog(module = "字典管理", type = OperationLog.OperationType.UPDATE, description = "更新字典数据")
    public Mono<ServerResponse> updateData(ServerRequest request) {
        Long id = dataId(request);
        return request.bodyToMono(DictDataRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(this::validateAndThrow)
                .flatMap(body -> dictService.updateData(id, body))
                .flatMap(this::ok);
    }

    /**
     * 删除字典数据。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为 {@code {"deleted": 是否删除成功}}
     * @throws BusinessException 当 {@code id} 非法时抛出
     */
    @RequirePermission("system:dict:remove")
    @Operation(operationId = "adminDeleteDictData", summary = "删除字典数据")
    @OperationLog(module = "字典管理", type = OperationLog.OperationType.DELETE, description = "删除字典数据")
    public Mono<ServerResponse> deleteData(ServerRequest request) {
        return dictService.deleteData(dataId(request)).flatMap(deleted -> ok(java.util.Map.of("deleted", deleted)));
    }

    /**
     * 启用或停用字典数据。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为更新后的字典数据实体
     * @throws BusinessException 当 {@code id} 非法或请求体为空时抛出
     */
    @RequirePermission("system:dict:edit")
    @Operation(operationId = "adminUpdateDictDataStatus", summary = "启用或停用字典数据")
    @OperationLog(module = "字典管理", type = OperationLog.OperationType.UPDATE, description = "更新字典数据状态")
    public Mono<ServerResponse> updateDataStatus(ServerRequest request) {
        Long id = dataId(request);
        return request.bodyToMono(StatusRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(this::validateAndThrow)
                .flatMap(body -> dictService.updateDataStatus(id, body.isEnabled()))
                .flatMap(this::ok);
    }

    /**
     * 解析并校验路径参数 {@code id}（字典类型）。
     *
     * @param request HTTP 请求
     * @return 大于 0 的字典类型 ID
     * @throws BusinessException 当参数缺失、非数字或不大于 0 时抛出
     */
    private Long typeId(ServerRequest request) {
        Long id = safeParseLong(request.pathVariable("id"));
        if (id == null || id <= 0) throw new BusinessException(ResultCode.PARAM_ERROR, "id格式错误");
        return id;
    }

    /**
     * 解析并校验路径参数 {@code id}（字典数据）。
     *
     * @param request HTTP 请求
     * @return 大于 0 的字典数据 ID
     * @throws BusinessException 当参数缺失、非数字或不大于 0 时抛出
     */
    private Long dataId(ServerRequest request) {
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

    /**
     * 状态更新请求体。
     *
     * <p>仅承载 {@code enabled} 布尔值，表示启用或停用目标对象。</p>
     */
    public static class StatusRequest {

        /** 是否启用：true-启用，false-停用 */
        private boolean enabled;

        /**
         * 构造状态更新请求体。
         */
        public StatusRequest() {}

        /**
         * 获取是否启用。
         *
         * @return true-启用，false-停用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用。
         *
         * @param enabled true-启用，false-停用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
