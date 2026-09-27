package com.apex.scaffold.domain.workflow;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * 工作流定义聚合根。
 *
 * <p>封装工作流定义的完整配置，包括节点、边、审批规则等。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
public class WorkflowDefinitionAggregate extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private Long tenantId;
    private String code;
    private String name;
    private String description;
    private Integer version;
    private Boolean enabled;
    private List<WorkflowNode> nodes;
    private List<WorkflowEdge> edges;

    protected WorkflowDefinitionAggregate() {
        super();
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
        this.version = 1;
        this.enabled = true;
    }

    /**
     * 创建工作流定义聚合根
     *
     * @param tenantId 租户 ID
     * @param code 工作流编码
     * @param name 工作流名称
     * @param description 工作流描述
     * @return 版本号为 1、默认启用且已初始化时间戳的工作流定义聚合根
     */
    public static WorkflowDefinitionAggregate create(Long tenantId, String code, String name, String description) {
        WorkflowDefinitionAggregate aggregate = new WorkflowDefinitionAggregate();
        aggregate.tenantId = tenantId;
        aggregate.code = code;
        aggregate.name = name;
        aggregate.description = description;
        aggregate.initTimestamps();
        return aggregate;
    }

    /**
     * 添加工作流节点
     *
     * @param node 待添加的工作流节点
     */
    public void addNode(WorkflowNode node) {
        this.nodes.add(node);
        touch();
    }

    /**
     * 添加工作流边（节点间连线）
     *
     * @param edge 待添加的工作流边
     */
    public void addEdge(WorkflowEdge edge) {
        this.edges.add(edge);
        touch();
    }

    /**
     * 启用工作流定义
     */
    public void enable() {
        this.enabled = true;
        touch();
    }

    /**
     * 停用工作流定义
     */
    public void disable() {
        this.enabled = false;
        touch();
    }

    /**
     * 设置租户 ID
     *
     * @param tenantId 租户 ID
     */
    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * 设置工作流编码
     *
     * @param code 工作流编码
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * 设置工作流名称
     *
     * @param name 工作流名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 设置工作流描述
     *
     * @param description 工作流描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 设置工作流版本号
     *
     * @param version 工作流版本号
     */
    public void setVersion(Integer version) {
        this.version = version;
    }

    /**
     * 设置启用状态
     *
     * @param enabled 是否启用
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 设置节点列表
     *
     * @param nodes 节点列表，为 null 时重置为空列表
     */
    public void setNodes(List<WorkflowNode> nodes) {
        this.nodes = nodes != null ? nodes : new ArrayList<>();
    }

    /**
     * 设置边列表
     *
     * @param edges 边列表，为 null 时重置为空列表
     */
    public void setEdges(List<WorkflowEdge> edges) {
        this.edges = edges != null ? edges : new ArrayList<>();
    }
}
