package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.DictDataRequest;
import com.apex.scaffold.application.dto.admin.DictTypeRequest;
import com.apex.scaffold.application.dto.admin.StatusRequest;
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
        return dictService.listTypes().flatMap(this::buildSuccess);
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
        return dictService.getType(requirePositiveId(request)).flatMap(this::buildSuccess);
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
        return parseBody(request, DictTypeRequest.class)
                .flatMap(dictService::createType)
                .flatMap(this::buildSuccess);
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
        Long id = requirePositiveId(request);
        return parseBody(request, DictTypeRequest.class)
                .flatMap(body -> dictService.updateType(id, body))
                .flatMap(this::buildSuccess);
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
        return dictService.deleteType(requirePositiveId(request)).flatMap(deleted -> buildSuccess(java.util.Map.of("deleted", deleted)));
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
        Long id = requirePositiveId(request);
        return parseBody(request, StatusRequest.class)
                .flatMap(body -> dictService.updateTypeStatus(id, body.isEnabled()))
                .flatMap(this::buildSuccess);
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
        return dictService.listDataByType(dictType).flatMap(this::buildSuccess);
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
        return dictService.getData(requirePositiveId(request)).flatMap(this::buildSuccess);
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
        return parseBody(request, DictDataRequest.class)
                .flatMap(dictService::createData)
                .flatMap(this::buildSuccess);
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
        Long id = requirePositiveId(request);
        return parseBody(request, DictDataRequest.class)
                .flatMap(body -> dictService.updateData(id, body))
                .flatMap(this::buildSuccess);
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
        return dictService.deleteData(requirePositiveId(request)).flatMap(deleted -> buildSuccess(java.util.Map.of("deleted", deleted)));
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
        Long id = requirePositiveId(request);
        return parseBody(request, StatusRequest.class)
                .flatMap(body -> dictService.updateDataStatus(id, body.isEnabled()))
                .flatMap(this::buildSuccess);
    }
}
