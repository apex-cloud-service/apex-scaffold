package com.apex.scaffold.contract.dto.internal;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 部门树节点（内部 API 契约）.
 *
 * <p>由 system-service 的 {@code GET /internal/v1/depts/tree} 提供，
 * 供 office-service（组织同步）、workflow-service（审批人按部门选择）消费。
 * 采用自包含的树结构（{@code children} 递归），消费方无需二次组装。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Data
@Builder
@AllArgsConstructor
public class DeptTreeDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 部门 ID */
    private Long deptId;

    /** 父部门 ID（根节点为 0 或 {@code null}，按提供方约定） */
    private Long parentId;

    /** 部门名称 */
    private String deptName;

    /** 租户 ID */
    private Long tenantId;

    /** 排序序号 */
    private Integer sortOrder;

    /**
     * 子部门列表（树形递归）。
     *
     * <p>由无参构造器初始化为空列表，避免消费方遍历时的 {@code null} 判空；
     * 遵循 Alibaba POJO 规范，字段声明处不设默认值。
     */
    private List<DeptTreeDTO> children;

    /**
     * 无参构造器（供反序列化与 POJO 规范使用），将子部门列表初始化为空列表.
     */
    public DeptTreeDTO() {
        this.children = new ArrayList<>();
    }
}
