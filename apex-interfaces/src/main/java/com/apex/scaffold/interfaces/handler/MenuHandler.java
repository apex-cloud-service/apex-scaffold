package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.MenuRequest;
import com.apex.scaffold.application.dto.admin.StatusRequest;
import com.apex.scaffold.application.service.MenuService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 系统菜单处理器（函数式端点）。
 *
 * <p>提供管理后台菜单树的查询、创建、更新、删除与启停管理 API，
 * 所有端点要求 {@code system:menu:*} 权限，是"角色-菜单-权限"
 * 管理闭环的接口层入口。</p>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /admin-api/v1/menus - 查询菜单树</li>
 *   <li>GET /admin-api/v1/menus/{menuId} - 查询菜单详情</li>
 *   <li>POST /admin-api/v1/menus - 创建菜单</li>
 *   <li>PUT /admin-api/v1/menus/{menuId} - 更新菜单</li>
 *   <li>DELETE /admin-api/v1/menus/{menuId} - 删除菜单</li>
 *   <li>PUT /admin-api/v1/menus/{menuId}/status - 启用/停用菜单</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see MenuService
 */
@Component
@Tag(name = "后台-菜单管理", description = "系统菜单树与按钮权限管理")
public class MenuHandler extends BaseHandler {

    /**
     * 系统菜单应用服务。
     */
    private final MenuService menuService;

    /**
     * 创建系统菜单处理器。
     *
     * @param menuService 系统菜单应用服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public MenuHandler(MenuService menuService, MessageUtils messageUtils, Validator validator) {
        super(messageUtils, validator);
        this.menuService = menuService;
    }

    /**
     * 查询完整菜单树。
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为菜单树根节点列表
     */
    @RequirePermission("system:menu:list")
    @Operation(operationId = "adminListMenus", summary = "查询菜单树")
    public Mono<ServerResponse> tree(ServerRequest request) {
        return menuService.getMenuTree().flatMap(this::buildSuccess);
    }

    /**
     * 查询菜单详情。
     *
     * @param request HTTP 请求，路径参数 {@code menuId} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为菜单实体
     * @throws BusinessException 当 {@code menuId} 非法或菜单不存在时抛出
     */
    @RequirePermission("system:menu:query")
    @Operation(operationId = "adminGetMenu", summary = "查询菜单详情")
    public Mono<ServerResponse> detail(ServerRequest request) {
        return menuService.getMenu(requirePositiveId(request)).flatMap(this::buildSuccess);
    }

    /**
     * 创建菜单。
     *
     * <p>请求体必填并经 Jakarta Bean Validation 校验。</p>
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为已创建的菜单实体
     * @throws BusinessException 当请求体为空时，错误码为 {@code PARAM_ERROR}
     */
    @RequirePermission("system:menu:add")
    @Operation(operationId = "adminCreateMenu", summary = "创建菜单")
    @OperationLog(module = "菜单管理", type = OperationLog.OperationType.INSERT, description = "创建菜单")
    public Mono<ServerResponse> create(ServerRequest request) {
        return parseBody(request, MenuRequest.class)
                .flatMap(menuService::createMenu)
                .flatMap(this::buildSuccess);
    }

    /**
     * 更新菜单。
     *
     * @param request HTTP 请求，路径参数 {@code menuId} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为已更新的菜单实体
     * @throws BusinessException 当 {@code menuId} 非法或请求体为空时抛出
     */
    @RequirePermission("system:menu:edit")
    @Operation(operationId = "adminUpdateMenu", summary = "更新菜单")
    @OperationLog(module = "菜单管理", type = OperationLog.OperationType.UPDATE, description = "更新菜单")
    public Mono<ServerResponse> update(ServerRequest request) {
        Long menuId = requirePositiveId(request);
        return parseBody(request, MenuRequest.class)
                .flatMap(body -> menuService.updateMenu(menuId, body))
                .flatMap(this::buildSuccess);
    }

    /**
     * 删除菜单。
     *
     * <p>含子菜单的父节点将被拒绝删除。</p>
     *
     * @param request HTTP 请求，路径参数 {@code menuId} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为 {@code {"deleted": 是否删除成功}}
     * @throws BusinessException 当 {@code menuId} 非法时抛出
     */
    @RequirePermission("system:menu:remove")
    @Operation(operationId = "adminDeleteMenu", summary = "删除菜单")
    @OperationLog(module = "菜单管理", type = OperationLog.OperationType.DELETE, description = "删除菜单")
    public Mono<ServerResponse> delete(ServerRequest request) {
        return menuService
                .deleteMenu(requirePositiveId(request))
                .flatMap(deleted -> buildSuccess(java.util.Map.of("deleted", deleted)));
    }

    /**
     * 启用或停用菜单。
     *
     * <p>请求体携带 {@code enabled} 布尔值。</p>
     *
     * @param request HTTP 请求，路径参数 {@code menuId} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为更新后的菜单实体
     * @throws BusinessException 当 {@code menuId} 非法或请求体为空时抛出
     */
    @RequirePermission("system:menu:edit")
    @Operation(operationId = "adminUpdateMenuStatus", summary = "启用或停用菜单")
    @OperationLog(module = "菜单管理", type = OperationLog.OperationType.UPDATE, description = "更新菜单状态")
    public Mono<ServerResponse> updateStatus(ServerRequest request) {
        Long menuId = requirePositiveId(request);
        return parseBody(request, StatusRequest.class)
                .flatMap(body -> menuService.updateStatus(menuId, body.isEnabled()))
                .flatMap(this::buildSuccess);
    }
}
