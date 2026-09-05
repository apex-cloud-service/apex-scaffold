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

    public static WorkflowDefinitionAggregate create(Long tenantId, String code, String name, String description) {
        WorkflowDefinitionAggregate aggregate = new WorkflowDefinitionAggregate();
        aggregate.tenantId = tenantId;
        aggregate.code = code;
        aggregate.name = name;
        aggregate.description = description;
        aggregate.initTimestamps();
        return aggregate;
    }

    public void addNode(WorkflowNode node) {
        this.nodes.add(node);
        touch();
    }

    public void addEdge(WorkflowEdge edge) {
        this.edges.add(edge);
        touch();
    }

    public void enable() {
        this.enabled = true;
        touch();
    }

    public void disable() {
        this.enabled = false;
        touch();
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public void setNodes(List<WorkflowNode> nodes) {
        this.nodes = nodes != null ? nodes : new ArrayList<>();
    }

    public void setEdges(List<WorkflowEdge> edges) {
        this.edges = edges != null ? edges : new ArrayList<>();
    }
}
