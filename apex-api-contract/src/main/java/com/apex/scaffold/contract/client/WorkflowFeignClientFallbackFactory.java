package com.apex.scaffold.contract.client;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.contract.dto.internal.InstanceStatusDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * {@link WorkflowFeignClient} 降级工厂.
 *
 * <p>当 workflow-service 不可用（超时/熔断/宕机）时返回 {@code SERVICE_UNAVAILABLE}，
 * 由调用方决定快速失败还是沿用本地缓存/事件副本状态。降级动作记录 WARN 日志便于告警。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Component
public class WorkflowFeignClientFallbackFactory implements FallbackFactory<WorkflowFeignClient> {

    private static final Logger log = LoggerFactory.getLogger(WorkflowFeignClientFallbackFactory.class);

    @Override
    public WorkflowFeignClient create(Throwable cause) {
        return new WorkflowFeignClient() {
            @Override
            public Result<InstanceStatusDTO> getInstanceStatus(String instanceId) {
                log.warn("workflow-service 不可用, 降级返回服务不可用: instanceId={}, cause={}", instanceId, cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
