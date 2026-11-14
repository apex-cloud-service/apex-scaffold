package com.apex.scaffold.application.service;

import com.apex.scaffold.application.assembler.MenuAssembler;
import com.apex.scaffold.application.dto.admin.MenuDTO;
import com.apex.scaffold.application.dto.admin.MenuRequest;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.MenuEntity;
import com.apex.scaffold.domain.repository.MenuRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 系统菜单应用服务。
 *
 * <p>提供菜单的树形查询、创建、更新、删除与启停管理能力，
 * 是管理后台"角色-菜单-权限"闭环的基础支撑。菜单以父子树形结构
 * 组织，服务层负责结构校验与树组装。</p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>按钮类型（menuType=2）菜单必须携带权限标识 {@code perms}</li>
 *   <li>创建子菜单时父菜单必须存在，且父菜单不能是按钮类型</li>
 *   <li>含子菜单的父节点禁止删除（{@code MENU_HAS_CHILDREN}）</li>
 *   <li>菜单树按父 ID、显示顺序升序稳定排序</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService {

    /**
     * 系统菜单仓储。
     */
    private final MenuRepository menuRepository;

    /**
     * 菜单对象转换器。
     */
    private final MenuAssembler menuAssembler;

    /**
     * 租户写链路守卫。
     */
    private final TenantGuard tenantGuard;

    private final TenantPackageService tenantPackageService;

    /**
     * 查询完整菜单树。
     *
     * <p>从可信租户上下文解析套餐范围后加载菜单；没有租户上下文时返回空列表，
     * 不得隐式降级为平台默认租户。拒绝范围直接返回空列表且不访问菜单仓储；
     * 受限套餐按 {@code menu_ids} 过滤并补全祖先链，保证返回树结构完整。</p>
     *
     * @return 菜单树根节点列表；无租户上下文或套餐拒绝授权时为空列表
     */
    public Mono<List<MenuDTO>> getMenuTree() {
        return tenantContextTenantCode()
                .flatMap(tenantPackageService::resolveScope)
                .flatMap(scope -> {
                    if (scope.denied()) {
                        return Mono.<List<MenuDTO>>just(List.of());
                    }
                    return menuRepository.findAll().map(entities -> buildScopedTree(entities, scope));
                })
                .switchIfEmpty(Mono.just(List.of()));
    }

    /**
     * 按套餐范围过滤菜单并组装菜单树。
     *
     * @param entities 未删除的完整菜单实体
     * @param scope 当前租户套餐范围
     * @return 菜单树根节点列表
     */
    private List<MenuDTO> buildScopedTree(List<MenuEntity> entities, TenantPackageService.PackageScope scope) {
        if (!scope.unbounded()) {
            var byId = new java.util.HashMap<Long, Long>();
            entities.forEach(entity -> byId.put(entity.getId(), entity.getParentId()));
            var allowed = scope.menuIds() == null ? java.util.Set.<Long>of() : scope.menuIds();
            var completed = new java.util.HashSet<>(allowed);
            allowed.forEach(id -> {
                Long parent = byId.get(id);
                int guard = 0;
                while (parent != null
                        && parent > 0
                        && parent != MenuEntity.ROOT_PARENT_ID
                        && completed.add(parent)
                        && guard++ < 32) {
                    parent = byId.get(parent);
                }
            });
            entities = entities.stream()
                    .filter(entity -> completed.contains(entity.getId()))
                    .collect(java.util.stream.Collectors.toList());
        }
        return buildTree(entities);
    }

    /**
     * 从 Reactor Context 读取当前租户编码。
     *
     * <p>上下文不存在时返回空 {@link Mono}，由调用方 fail-closed 处理；
     * 本方法不提供平台租户默认值。</p>
     *
     * @return 当前租户编码；无上下文时为空
     */
    private Mono<String> tenantContextTenantCode() {
        return Mono.deferContextual(context -> Mono.justOrEmpty(context.getOrEmpty(TenantContext.REACTOR_CONTEXT_KEY)))
                .map(Object::toString);
    }

    /**
     * 查询菜单详情。
     *
     * @param id 菜单 ID
     * @return 菜单实体
     * @throws BusinessException 当菜单不存在时抛出 {@code MENU_NOT_FOUND}
     */
    public Mono<MenuEntity> getMenu(Long id) {
        return menuRepository.findById(id).switchIfEmpty(Mono.error(new BusinessException(ResultCode.MENU_NOT_FOUND)));
    }

    /**
     * 创建菜单。
     *
     * <p>校验父菜单存在性与按钮权限标识后持久化，成功后记录创建日志。</p>
     *
     * @param request 菜单请求体
     * @return 已创建的菜单实体
     * @throws BusinessException 校验失败时抛出对应业务异常
     */
    public Mono<MenuEntity> createMenu(MenuRequest request) {
        return tenantGuard
                .assertActive()
                .then(validateParent(request.getParentId()))
                .then(validateButtonPerms(request))
                .then(Mono.defer(() -> {
                    MenuEntity entity = MenuEntity.create(
                            request.getParentId(),
                            request.getMenuName(),
                            request.getMenuType(),
                            request.getPath(),
                            request.getComponent(),
                            request.getPerms(),
                            request.getSort());
                    entity.setIcon(request.getIcon());
                    if (request.getVisible() != null) {
                        entity.setVisible(request.getVisible());
                    }
                    if (request.getStatus() != null) {
                        entity.setStatus(request.getStatus());
                    }
                    return menuRepository.insert(entity).map(rows -> {
                        log.info("创建菜单成功，name: {}, type: {}", entity.getMenuName(), entity.getMenuType());
                        return entity;
                    });
                }));
    }

    /**
     * 更新菜单。
     *
     * <p>菜单不存在时抛出 {@code MENU_NOT_FOUND}；按钮权限校验通过后
     * 更新展示与路由属性。</p>
     *
     * @param id 菜单 ID
     * @param request 菜单请求体
     * @return 已更新的菜单实体
     * @throws BusinessException 菜单不存在或校验失败时抛出
     */
    public Mono<MenuEntity> updateMenu(Long id, MenuRequest request) {
        return getMenu(id)
                .flatMap(existing -> validateButtonPerms(request).thenReturn(existing))
                .flatMap(existing -> {
                    existing.updateInfo(
                            request.getMenuName(),
                            request.getPath(),
                            request.getComponent(),
                            request.getPerms(),
                            request.getIcon(),
                            request.getSort());
                    if (request.getParentId() != null
                            && !Objects.equals(request.getParentId(), existing.getParentId())) {
                        return validateParent(request.getParentId())
                                .thenReturn(existing)
                                .flatMap(e -> {
                                    e.moveTo(request.getParentId());
                                    return menuRepository.updateById(e).map(rows -> e);
                                });
                    }
                    return menuRepository.updateById(existing).map(rows -> existing);
                })
                .doOnSuccess(menu -> log.info("更新菜单成功，id: {}", menu.getId()));
    }

    /**
     * 删除菜单。
     *
     * <p>含子菜单的父节点禁止删除；删除为逻辑删除。</p>
     *
     * @param id 菜单 ID
     * @return 删除结果，true-删除成功，false-未删除任何记录
     * @throws BusinessException 菜单不存在或存在子菜单时抛出
     */
    public Mono<Boolean> deleteMenu(Long id) {
        return getMenu(id)
                .flatMap(menu -> menuRepository.existsByParentId(id).flatMap(hasChildren -> {
                    if (hasChildren) {
                        return Mono.error(new BusinessException(ResultCode.MENU_HAS_CHILDREN));
                    }
                    return menuRepository.deleteById(id).map(rows -> rows > 0);
                }))
                .doOnSuccess(deleted -> log.info("删除菜单完成，id: {}, deleted: {}", id, deleted));
    }

    /**
     * 启用或停用菜单。
     *
     * @param id 菜单 ID
     * @param enabled true-启用，false-停用
     * @return 更新后的菜单实体
     * @throws BusinessException 菜单不存在时抛出
     */
    public Mono<MenuEntity> updateStatus(Long id, boolean enabled) {
        return getMenu(id)
                .flatMap(menu -> {
                    if (enabled) {
                        menu.enable();
                    } else {
                        menu.disable();
                    }
                    return menuRepository.updateById(menu).map(rows -> menu);
                })
                .doOnSuccess(menu -> log.info("更新菜单状态完成，id: {}, enabled: {}", id, enabled));
    }

    /**
     * 校验父菜单存在性。
     *
     * <p>顶级菜单（parentId 为 0 或 null）直接放行；
     * 非顶级菜单要求父节点存在且不是按钮类型。</p>
     *
     * @param parentId 父菜单 ID
     * @throws BusinessException 父菜单不存在或为按钮类型时抛出
     */
    private Mono<Void> validateParent(Long parentId) {
        if (parentId == null || parentId == MenuEntity.ROOT_PARENT_ID) {
            return Mono.empty();
        }
        return menuRepository
                .findById(parentId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.MENU_PARENT_NOT_FOUND)))
                .flatMap(parent -> {
                    if (parent.isButton()) {
                        return Mono.error(new BusinessException(ResultCode.MENU_PARENT_NOT_FOUND, "按钮类型不能作为父菜单"));
                    }
                    return Mono.empty();
                });
    }

    /**
     * 校验按钮类型菜单必须配置权限标识。
     *
     * @param request 菜单请求体
     * @throws BusinessException 按钮类型缺少权限标识时抛出
     */
    private Mono<Void> validateButtonPerms(MenuRequest request) {
        if (isButtonTypeMenuMissingPerms(request)) {
            return Mono.error(new BusinessException(ResultCode.MENU_PERMS_REQUIRED));
        }
        return Mono.empty();
    }

    /**
     * 检查是否为按钮类型菜单且缺少权限标识。
     *
     * <p>业务规则：按钮类型菜单（menuType=2）必须配置权限标识，
     * 权限标识用于后端权限校验，缺失则无法控制按钮访问。
     *
     * @param request 菜单请求体
     * @return true 表示是按钮类型且缺少权限标识
     */
    private boolean isButtonTypeMenuMissingPerms(MenuRequest request) {
        return request.getMenuType() != null
                && request.getMenuType() == 2
                && (request.getPerms() == null || request.getPerms().isBlank());
    }

    /**
     * 将扁平菜单实体列表组装为树形结构。
     *
     * <p>先按 ID 建索引，再按父 ID 挂载子节点，最后提取顶级根节点列表。
     * 同级子节点按显示顺序升序排列。</p>
     *
     * @param entities 扁平菜单实体列表
     * @return 菜单树根节点列表
     */
    private List<MenuDTO> buildTree(List<MenuEntity> entities) {
        List<MenuDTO> nodes = entities.stream().map(menuAssembler::toDTO).collect(Collectors.toList());
        Map<Long, MenuDTO> byId = nodes.stream().collect(Collectors.toMap(MenuDTO::getId, node -> node));
        List<MenuDTO> roots = new ArrayList<>(16);
        for (MenuDTO node : nodes) {
            if (node.getParentId() == null || node.getParentId() == MenuEntity.ROOT_PARENT_ID) {
                roots.add(node);
            } else {
                MenuDTO parent = byId.get(node.getParentId());
                if (parent != null) {
                    parent.addChild(node);
                } else {
                    // 父节点已被逻辑删除或不存在，按顶级节点兜底处理
                    roots.add(node);
                }
            }
        }
        sortTree(roots);
        return roots;
    }

    /**
     * 递归对菜单树进行同级排序。
     *
     * @param nodes 待排序的节点列表
     */
    private void sortTree(List<MenuDTO> nodes) {
        if (nodes == null) {
            return;
        }
        nodes.sort(Comparator.comparing(MenuDTO::getSort, Comparator.nullsLast(Integer::compareTo)));
        nodes.forEach(node -> {
            if (node.getChildren() != null) {
                sortTree(node.getChildren());
            }
        });
    }
}
