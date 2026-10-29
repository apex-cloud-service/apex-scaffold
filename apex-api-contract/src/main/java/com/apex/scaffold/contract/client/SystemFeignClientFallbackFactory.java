package com.apex.scaffold.contract.client;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.contract.dto.internal.DeptTreeDTO;
import com.apex.scaffold.contract.dto.internal.DictItemDTO;
import com.apex.scaffold.contract.dto.internal.UserSnapshotDTO;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * {@link SystemFeignClient} 降级工厂.
 *
 * <p>当 system-service 不可用（超时/熔断/宕机）时返回服务不可用语义，由调用方决定
 * 是快速失败还是使用本地缓存快照。降级动作记录 WARN 日志便于告警。
 *
 * <p><b>降级策略</b>：依据迁移方案 §6.3 的降级矩阵，system-service 的读契约均采用
 * 「拒绝优先 + 本地陈新缓存并联级」：本工厂统一返回 {@link ResultCode#SERVICE_UNAVAILABLE}，
 * 调用方可据业务容忍度选择直接失败或回退本地缓存。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Component
@Slf4j
public class SystemFeignClientFallbackFactory implements FallbackFactory<SystemFeignClient> {

    @Override
    public SystemFeignClient create(Throwable cause) {
        return new SystemFeignClient() {
            @Override
            public Result<UserSnapshotDTO> getUserSnapshot(Long accountId) {
                log.warn("system-service 不可用, 降级返回服务不可用: accountId={}, cause={}", accountId, cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public Result<UserSnapshotDTO> getUserProfile(Long accountId) {
                log.warn("system-service 不可用, 降级返回服务不可用: accountId={}, cause={}", accountId, cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public Result<List<DeptTreeDTO>> getDeptTree() {
                log.warn("system-service 不可用, 降级返回服务不可用: deptTree, cause={}", cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public Result<List<DictItemDTO>> getDictItems(String type) {
                log.warn("system-service 不可用, 降级返回服务不可用: dictType={}, cause={}", type, cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
