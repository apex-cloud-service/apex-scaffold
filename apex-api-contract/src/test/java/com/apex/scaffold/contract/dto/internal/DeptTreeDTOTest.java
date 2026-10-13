package com.apex.scaffold.contract.dto.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link DeptTreeDTO} 部门树节点契约测试.
 *
 * <p>校验内部 API 契约 DTO 的构造约定：无参构造器须将 {@code children} 初始化为空列表
 * （避免消费方遍历时判空），并支持 builder 构建自包含的递归树结构。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("DeptTreeDTO 部门树节点契约测试")
class DeptTreeDTOTest {

    @Test
    @DisplayName("无参构造器将 children 初始化为空列表，避免消费方判空")
    void noArgConstructorInitializesChildren() {
        DeptTreeDTO node = new DeptTreeDTO();
        assertThat(node.getChildren()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("builder 可构建自包含的递归树结构")
    void builderBuildsRecursiveTree() {
        DeptTreeDTO child =
                DeptTreeDTO.builder().deptId(2L).parentId(1L).deptName("研发部").build();
        DeptTreeDTO root = DeptTreeDTO.builder()
                .deptId(1L)
                .parentId(0L)
                .deptName("总部")
                .tenantId(9L)
                .sortOrder(1)
                .children(List.of(child))
                .build();

        assertThat(root.getDeptId()).isEqualTo(1L);
        assertThat(root.getParentId()).isZero();
        assertThat(root.getTenantId()).isEqualTo(9L);
        assertThat(root.getSortOrder()).isEqualTo(1);
        assertThat(root.getChildren()).extracting(DeptTreeDTO::getDeptName).containsExactly("研发部");
    }
}
