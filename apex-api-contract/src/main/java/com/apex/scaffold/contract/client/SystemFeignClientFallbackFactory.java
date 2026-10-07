package com.apex.scaffold.contract.client;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.contract.dto.internal.UserSnapshotDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * {@link SystemFeignClient} 降级工厂.
 *
 * <p>当 system-service 不可用（超时/熔断/宕机）时返回服务不可用语义，由调用方决定
 * 是快速失败还是使用本地缓存快照。降级动作记录 WARN 日志便于告警。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Component
public class SystemFeignClientFallbackFactory implements FallbackFactory<SystemFeignClient> {

    private static final Logger log = LoggerFactory.getLogger(SystemFeignClientFallbackFactory.class);

    @Override
    public SystemFeignClient create(Throwable cause) {
        return new SystemFeignClient() {
            @Override
            public Result<UserSnapshotDTO> getUserSnapshot(Long accountId) {
                log.warn("system-service 不可用, 降级返回服务不可用: accountId={}, cause={}", accountId, cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
