package com.apex.scaffold.contract.client;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.contract.config.FeignClientConstants;
import com.apex.scaffold.contract.config.FeignTenantTraceConfiguration;
import com.apex.scaffold.contract.dto.internal.InstanceStatusDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 工作流服务内部 Feign 客户端.
 *
 * <p>提供方：apex-workflow-service<br/>
 * 消费方：office-service（审批联动推送）、system-service（待办/通知）
 *
 * <p><b>内部 API 契约</b>：路径前缀 {@code /internal/v1/}，不经过网关；
 * 通过 {@link FeignTenantTraceConfiguration} 自动注入 {@code X-Tenant-Id} / {@code X-Trace-Id} 头。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@FeignClient(
        name = FeignClientConstants.WORKFLOW_SERVICE_NAME,
        contextId = "workflowFeignClient",
        configuration = FeignTenantTraceConfiguration.class,
        fallbackFactory = WorkflowFeignClientFallbackFactory.class)
public interface WorkflowFeignClient {

    /**
     * 查询流程实例的当前审批状态.
     *
     * <p>用途：消费方在推送审批通知、渲染待办列表、触发联动动作时，向 workflow-service
     * 查询实例状态的最新语义（运行中/已完成/已终止 + 当前节点）。
     *
     * @param instanceId 流程实例 ID
     * @return 实例状态快照
     */
    @GetMapping("/internal/v1/instances/{id}/status")
    Result<InstanceStatusDTO> getInstanceStatus(@PathVariable("id") String instanceId);
}
