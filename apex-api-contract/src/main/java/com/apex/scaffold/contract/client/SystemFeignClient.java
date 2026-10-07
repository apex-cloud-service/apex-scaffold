package com.apex.scaffold.contract.client;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.contract.config.FeignClientConstants;
import com.apex.scaffold.contract.dto.internal.UserSnapshotDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 系统管理服务内部 Feign 客户端.
 *
 * <p>提供方：apex-system-service<br/>
 * 消费方：apex-auth-service / apex-office-service 等
 *
 * <p><b>内部 API 契约</b>：路径前缀 {@code /internal/v1/}，不经过网关，仅集群内直连。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@FeignClient(
        name = FeignClientConstants.SYSTEM_SERVICE_NAME,
        contextId = "systemFeignClient",
        fallbackFactory = SystemFeignClientFallbackFactory.class)
public interface SystemFeignClient {

    /**
     * 获取用户档案快照（只读副本）.
     *
     * <p>用途：
     * <ul>
     *   <li>auth-service：登录时查询用户基本信息用于 Token 签发</li>
     *   <li>office-service：组织同步时获取用户档案副本</li>
     * </ul>
     *
     * @param accountId 统一账户 ID
     * @return 用户快照（含权限列表，仅当提供方按需展开）
     */
    @GetMapping("/internal/v1/users/{accountId}/snapshot")
    Result<UserSnapshotDTO> getUserSnapshot(@PathVariable("accountId") Long accountId);
}
