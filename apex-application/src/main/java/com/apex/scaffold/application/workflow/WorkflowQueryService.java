package com.apex.scaffold.application.workflow;

import com.apex.scaffold.application.dto.WorkflowDefinitionDTO;
import com.apex.scaffold.application.dto.WorkflowInstanceDTO;
import com.apex.scaffold.application.dto.WorkflowInstanceDetailDTO;
import com.apex.scaffold.application.dto.WorkflowTaskDTO;
import com.apex.scaffold.infrastructure.persistence.mapper.ProcessInstanceMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.TaskMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.WorkflowDefinitionMapper;
import com.apex.scaffold.infrastructure.persistence.po.ProcessInstancePO;
import com.apex.scaffold.infrastructure.persistence.po.TaskPO;
import com.apex.scaffold.infrastructure.persistence.po.WorkflowDefinitionPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 工作流查询服务。
 *
 * <p>提供流程实例查询、任务查询、详情查询等功能。</p>
 *
 * @author Apex Team
 * @version 1.4.0
 * @since 1.4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowQueryService {

    private final ProcessInstanceMapper processInstanceMapper;
    private final TaskMapper taskMapper;
    private final WorkflowDefinitionMapper workflowDefinitionMapper;
    private final ObjectMapper objectMapper;

    /**
     * 分页查询流程实例列表。
     *
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param status 状态过滤（可选）
     * @param initiatorId 发起人过滤（可选）
     * @return 分页结果
     */
    public Mono<IPage<WorkflowInstanceDTO>> listInstances(int pageNum, int pageSize, String status, Long initiatorId) {
        return Mono.fromCallable(() -> {
                    Page<ProcessInstancePO> page = new Page<>(pageNum, pageSize);
                    LambdaQueryWrapper<ProcessInstancePO> wrapper = new LambdaQueryWrapper<>();

                    if (status != null && !status.isEmpty()) {
                        wrapper.eq(ProcessInstancePO::getStatus, status);
                    }
                    if (initiatorId != null) {
                        wrapper.eq(ProcessInstancePO::getInitiatorId, initiatorId);
                    }
                    wrapper.orderByDesc(ProcessInstancePO::getCreateTime);

                    IPage<ProcessInstancePO> poPage = processInstanceMapper.selectPage(page, wrapper);

                    // 转换为 DTO
                    List<WorkflowInstanceDTO> dtoList = poPage.getRecords().stream()
                            .map(this::convertToInstanceDTO)
                            .collect(Collectors.toList());

                    Page<WorkflowInstanceDTO> dtoPage = new Page<>(pageNum, pageSize, poPage.getTotal());
                    dtoPage.setRecords(dtoList);
                    return (IPage<WorkflowInstanceDTO>) dtoPage;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取流程实例详情（包含定义和任务）。
     *
     * @param instanceId 流程实例 ID
     * @return 流程详情 DTO
     */
    public Mono<WorkflowInstanceDetailDTO> getInstanceDetail(Long instanceId) {
        return Mono.fromCallable(() -> {
                    WorkflowInstanceDetailDTO detail = new WorkflowInstanceDetailDTO();

                    // 查询流程实例
                    ProcessInstancePO instancePO = processInstanceMapper.selectById(instanceId);
                    if (instancePO == null) {
                        return null;
                    }
                    detail.setInstance(convertToInstanceDTO(instancePO));
                    detail.setCurrentNodeId(instancePO.getCurrentNodeCode());

                    // 查询流程定义
                    WorkflowDefinitionPO definitionPO =
                            workflowDefinitionMapper.selectById(instancePO.getWorkflowDefinitionId());
                    if (definitionPO != null) {
                        detail.setDefinition(convertToDefinitionDTO(definitionPO));
                    }

                    // 查询任务列表
                    List<TaskPO> taskPOs = taskMapper.selectList(new LambdaQueryWrapper<TaskPO>()
                            .eq(TaskPO::getProcessInstanceId, instanceId)
                            .orderByDesc(TaskPO::getCreateTime));
                    List<WorkflowTaskDTO> tasks =
                            taskPOs.stream().map(this::convertToTaskDTO).collect(Collectors.toList());
                    detail.setTasks(tasks);

                    return detail;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 查询用户待办任务。
     *
     * @param assigneeId 办理人 ID
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public Mono<IPage<WorkflowTaskDTO>> listMyTasks(Long assigneeId, int pageNum, int pageSize) {
        return Mono.fromCallable(() -> {
                    Page<TaskPO> page = new Page<>(pageNum, pageSize);
                    LambdaQueryWrapper<TaskPO> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(TaskPO::getAssigneeId, assigneeId)
                            .eq(TaskPO::getStatus, "PENDING")
                            .orderByDesc(TaskPO::getCreateTime);

                    IPage<TaskPO> poPage = taskMapper.selectPage(page, wrapper);

                    List<WorkflowTaskDTO> dtoList = poPage.getRecords().stream()
                            .map(this::convertToTaskDTO)
                            .collect(Collectors.toList());

                    Page<WorkflowTaskDTO> dtoPage = new Page<>(pageNum, pageSize, poPage.getTotal());
                    dtoPage.setRecords(dtoList);
                    return (IPage<WorkflowTaskDTO>) dtoPage;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取流程定义详情（用于设计器加载）。
     *
     * @param definitionId 流程定义 ID
     * @return 流程定义 DTO
     */
    public Mono<WorkflowDefinitionDTO> getDefinitionDetail(Long definitionId) {
        return Mono.fromCallable(() -> {
                    WorkflowDefinitionPO definitionPO = workflowDefinitionMapper.selectById(definitionId);
                    if (definitionPO == null) {
                        return null;
                    }
                    return convertToDefinitionDTO(definitionPO);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ========== 私有转换方法 ==========

    private WorkflowInstanceDTO convertToInstanceDTO(ProcessInstancePO po) {
        WorkflowInstanceDTO dto = new WorkflowInstanceDTO();
        dto.setId(po.getId());
        dto.setDefinitionId(po.getWorkflowDefinitionId());
        dto.setProcessCode(po.getBusinessKey()); // 简化映射
        dto.setTitle("流程实例-" + po.getId()); // 简化映射
        dto.setStatus(po.getStatus());
        dto.setInitiatorId(po.getInitiatorId());
        dto.setInitiatorName("用户-" + po.getInitiatorId()); // 简化映射，实际应关联用户表
        dto.setBusinessKey(po.getBusinessKey());
        dto.setCurrentNodeId(po.getCurrentNodeCode());
        dto.setStartTime(po.getStartTime());
        dto.setEndTime(po.getEndTime());
        dto.setCreateTime(po.getCreateTime());
        return dto;
    }

    private WorkflowTaskDTO convertToTaskDTO(TaskPO po) {
        WorkflowTaskDTO dto = new WorkflowTaskDTO();
        dto.setId(po.getId());
        dto.setInstanceId(po.getProcessInstanceId());
        dto.setTaskName(po.getTaskName());
        dto.setTaskType("USER_TASK"); // 简化映射
        dto.setStatus(po.getStatus());
        dto.setAssigneeId(po.getAssigneeId());
        dto.setAssigneeName("用户-" + po.getAssigneeId()); // 简化映射
        dto.setComment(po.getRemark());
        dto.setNodeId(po.getNodeCode());
        dto.setStartTime(po.getCreateTime());
        dto.setCompletedTime(po.getCompleteTime());
        dto.setCreateTime(po.getCreateTime());
        return dto;
    }

    private WorkflowDefinitionDTO convertToDefinitionDTO(WorkflowDefinitionPO po) {
        WorkflowDefinitionDTO dto = new WorkflowDefinitionDTO();
        dto.setId(po.getId());
        dto.setCode(po.getWorkflowCode());
        dto.setName(po.getWorkflowName());
        dto.setDescription(po.getDescription());
        dto.setVersion(po.getVersion());
        dto.setEnabled(po.getEnabled() == 1);

        // 解析 DAG JSON
        try {
            if (po.getNodeDefinition() != null && !po.getNodeDefinition().isEmpty()) {
                Map<String, Object> dag =
                        objectMapper.readValue(po.getNodeDefinition(), new TypeReference<Map<String, Object>>() {});

                // 解析节点
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> nodesJson = (List<Map<String, Object>>) dag.get("nodes");
                if (nodesJson != null) {
                    List<WorkflowDefinitionDTO.WorkflowNodeDTO> nodes =
                            nodesJson.stream().map(this::convertToNodeDTO).collect(Collectors.toList());
                    dto.setNodes(nodes);
                }

                // 解析连线
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> edgesJson = (List<Map<String, Object>>) dag.get("edges");
                if (edgesJson != null) {
                    List<WorkflowDefinitionDTO.WorkflowEdgeDTO> edges =
                            edgesJson.stream().map(this::convertToEdgeDTO).collect(Collectors.toList());
                    dto.setEdges(edges);
                }
            } else {
                dto.setNodes(new ArrayList<>());
                dto.setEdges(new ArrayList<>());
            }
        } catch (Exception e) {
            log.error("解析 DAG JSON 失败", e);
            dto.setNodes(new ArrayList<>());
            dto.setEdges(new ArrayList<>());
        }

        return dto;
    }

    private WorkflowDefinitionDTO.WorkflowNodeDTO convertToNodeDTO(Map<String, Object> json) {
        WorkflowDefinitionDTO.WorkflowNodeDTO node = new WorkflowDefinitionDTO.WorkflowNodeDTO();
        node.setNodeId((String) json.get("nodeId"));
        node.setNodeType((String) json.get("nodeType"));
        node.setNodeName((String) json.get("nodeName"));
        node.setApproverType((String) json.get("approverType"));
        node.setApproverValue((String) json.get("approverValue"));
        node.setApprovalStrategy((String) json.get("approvalStrategy"));
        node.setTimeoutHours((Integer) json.get("timeoutHours"));
        node.setTimeoutAction((String) json.get("timeoutAction"));

        @SuppressWarnings("unchecked")
        Map<String, Object> position = (Map<String, Object>) json.get("position");
        node.setPosition(position != null ? position : new HashMap<>());

        return node;
    }

    private WorkflowDefinitionDTO.WorkflowEdgeDTO convertToEdgeDTO(Map<String, Object> json) {
        WorkflowDefinitionDTO.WorkflowEdgeDTO edge = new WorkflowDefinitionDTO.WorkflowEdgeDTO();
        edge.setEdgeId((String) json.get("edgeId"));
        edge.setSourceNodeId((String) json.get("sourceNodeId"));
        edge.setTargetNodeId((String) json.get("targetNodeId"));
        edge.setCondition((String) json.get("condition"));
        return edge;
    }
}
