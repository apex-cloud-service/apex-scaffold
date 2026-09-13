package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.assembler.MenuAssembler;
import com.apex.scaffold.application.dto.admin.MenuDTO;
import com.apex.scaffold.application.dto.admin.MenuRequest;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.model.entity.MenuEntity;
import com.apex.scaffold.domain.repository.MenuRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 系统菜单应用服务单元测试。
 *
 * <p>覆盖菜单树组装、创建、更新、删除与启停等核心业务规则，
 * 重点验证父子关系校验、按钮权限标识校验与树形组装逻辑。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock
    private MenuRepository menuRepository;

    @Mock
    private MenuAssembler menuAssembler;

    @InjectMocks
    private MenuService menuService;

    /** 顶级目录实体 */
    private MenuEntity directory;

    /** 子菜单实体 */
    private MenuEntity childMenu;

    /** 顶级目录 DTO */
    private MenuDTO directoryDTO;

    /** 子菜单 DTO */
    private MenuDTO childMenuDTO;

    /**
     * 初始化测试数据。
     */
    @BeforeEach
    void setUp() {
        directory = MenuEntity.create(0L, "系统管理", 0, "system", "system/index", null, 1);
        directory.setId(1L);
        childMenu = MenuEntity.create(1L, "用户管理", 1, "system/user", "system/user/index", "system:user:list", 1);
        childMenu.setId(2L);

        directoryDTO = new MenuDTO();
        directoryDTO.setId(1L);
        directoryDTO.setParentId(0L);
        directoryDTO.setMenuName("系统管理");
        directoryDTO.setMenuType(0);
        directoryDTO.setPath("system");
        directoryDTO.setComponent("system/index");
        directoryDTO.setSort(1);

        childMenuDTO = new MenuDTO();
        childMenuDTO.setId(2L);
        childMenuDTO.setParentId(1L);
        childMenuDTO.setMenuName("用户管理");
        childMenuDTO.setMenuType(1);
        childMenuDTO.setPath("system/user");
        childMenuDTO.setComponent("system/user/index");
        childMenuDTO.setPerms("system:user:list");
        childMenuDTO.setSort(1);

        lenient().when(menuAssembler.toDTO(directory)).thenReturn(directoryDTO);
        lenient().when(menuAssembler.toDTO(childMenu)).thenReturn(childMenuDTO);
    }

    /**
     * 测试菜单树组装。
     */
    @Test
    @DisplayName("getMenuTree - 成功组装两级菜单树")
    void getMenuTree_BuildsTree() {
        when(menuRepository.findAll()).thenReturn(Mono.just(List.of(directory, childMenu)));

        menuService
                .getMenuTree()
                .as(StepVerifier::create)
                .assertNext(roots -> {
                    assertThat(roots).hasSize(1);
                    MenuDTO root = roots.get(0);
                    assertThat(root.getMenuName()).isEqualTo("系统管理");
                    assertThat(root.getChildren()).hasSize(1);
                    assertThat(root.getChildren().get(0).getMenuName()).isEqualTo("用户管理");
                })
                .verifyComplete();
    }

    /**
     * 测试菜单树对孤儿节点的兜底处理。
     */
    @Test
    @DisplayName("getMenuTree - 父节点缺失时按顶级节点兜底")
    void getMenuTree_OrphanNodeFallsBackToRoot() {
        when(menuRepository.findAll()).thenReturn(Mono.just(List.of(childMenu)));

        menuService
                .getMenuTree()
                .as(StepVerifier::create)
                .assertNext(roots -> assertThat(roots).hasSize(1))
                .verifyComplete();
    }

    /**
     * 测试创建菜单成功。
     */
    @Test
    @DisplayName("createMenu - 成功创建顶级菜单")
    void createMenu_Success() {
        MenuRequest request = new MenuRequest();
        request.setMenuName("用户管理");
        request.setMenuType(1);
        request.setPath("system/user");
        request.setComponent("system/user/index");
        request.setPerms("system:user:list");
        request.setSort(1);

        when(menuRepository.insert(any(MenuEntity.class))).thenReturn(Mono.just(1));

        menuService
                .createMenu(request)
                .as(StepVerifier::create)
                .assertNext(menu -> {
                    assertThat(menu.getMenuName()).isEqualTo("用户管理");
                    assertThat(menu.getParentId()).isEqualTo(MenuEntity.ROOT_PARENT_ID);
                })
                .verifyComplete();

        verify(menuRepository).insert(any(MenuEntity.class));
    }

    /**
     * 测试创建按钮菜单缺少权限标识被拒绝。
     */
    @Test
    @DisplayName("createMenu - 按钮类型缺少权限标识时抛出异常")
    void createMenu_ButtonWithoutPermsFails() {
        MenuRequest request = new MenuRequest();
        request.setMenuName("新增用户");
        request.setMenuType(2);

        menuService
                .createMenu(request)
                .as(StepVerifier::create)
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getCode()).isEqualTo(4704);
                })
                .verify();
    }

    /**
     * 测试创建子菜单时父菜单不存在被拒绝。
     */
    @Test
    @DisplayName("createMenu - 父菜单不存在时抛出异常")
    void createMenu_ParentNotFoundFails() {
        MenuRequest request = new MenuRequest();
        request.setParentId(99L);
        request.setMenuName("子菜单");
        request.setMenuType(1);

        when(menuRepository.findById(99L)).thenReturn(Mono.empty());

        menuService
                .createMenu(request)
                .as(StepVerifier::create)
                .expectError(BusinessException.class)
                .verify();
    }

    /**
     * 测试更新菜单成功。
     */
    @Test
    @DisplayName("updateMenu - 成功更新菜单信息")
    void updateMenu_Success() {
        MenuRequest request = new MenuRequest();
        request.setMenuName("用户管理-更新");
        request.setMenuType(1);
        request.setPath("system/user");
        request.setComponent("system/user/index");
        request.setPerms("system:user:list");
        request.setSort(2);

        when(menuRepository.findById(2L)).thenReturn(Mono.just(childMenu));
        when(menuRepository.updateById(any(MenuEntity.class))).thenReturn(Mono.just(1));

        menuService
                .updateMenu(2L, request)
                .as(StepVerifier::create)
                .assertNext(menu -> assertThat(menu.getMenuName()).isEqualTo("用户管理-更新"))
                .verifyComplete();
    }

    /**
     * 测试更新不存在的菜单抛出异常。
     */
    @Test
    @DisplayName("updateMenu - 菜单不存在时抛出异常")
    void updateMenu_NotFoundFails() {
        MenuRequest request = new MenuRequest();
        request.setMenuName("不存在");
        request.setMenuType(1);

        when(menuRepository.findById(99L)).thenReturn(Mono.empty());

        menuService
                .updateMenu(99L, request)
                .as(StepVerifier::create)
                .expectError(BusinessException.class)
                .verify();
    }

    /**
     * 测试删除含子菜单的父节点被拒绝。
     */
    @Test
    @DisplayName("deleteMenu - 存在子菜单时拒绝删除")
    void deleteMenu_HasChildrenFails() {
        when(menuRepository.findById(1L)).thenReturn(Mono.just(directory));
        when(menuRepository.existsByParentId(1L)).thenReturn(Mono.just(true));

        menuService
                .deleteMenu(1L)
                .as(StepVerifier::create)
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getCode()).isEqualTo(4703);
                })
                .verify();
    }

    /**
     * 测试删除叶子菜单成功。
     */
    @Test
    @DisplayName("deleteMenu - 成功删除叶子菜单")
    void deleteMenu_Success() {
        when(menuRepository.findById(2L)).thenReturn(Mono.just(childMenu));
        when(menuRepository.existsByParentId(2L)).thenReturn(Mono.just(false));
        when(menuRepository.deleteById(2L)).thenReturn(Mono.just(1));

        menuService.deleteMenu(2L).as(StepVerifier::create).expectNext(true).verifyComplete();
    }

    /**
     * 测试启用停用菜单。
     */
    @Test
    @DisplayName("updateStatus - 成功停用菜单")
    void updateStatus_Success() {
        when(menuRepository.findById(2L)).thenReturn(Mono.just(childMenu));
        when(menuRepository.updateById(any(MenuEntity.class))).thenReturn(Mono.just(1));

        menuService
                .updateStatus(2L, false)
                .as(StepVerifier::create)
                .assertNext(menu -> assertThat(menu.isEnabled()).isFalse())
                .verifyComplete();
    }
}
