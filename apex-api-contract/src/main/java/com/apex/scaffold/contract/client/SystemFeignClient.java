package com.apex.scaffold.contract.client;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.contract.config.FeignClientConstants;
import com.apex.scaffold.contract.config.FeignTenantTraceConfiguration;
import com.apex.scaffold.contract.dto.internal.DeptTreeDTO;
import com.apex.scaffold.contract.dto.internal.DictItemDTO;
import com.apex.scaffold.contract.dto.internal.UserSnapshotDTO;
import java.util.List;
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
        configuration = FeignTenantTraceConfiguration.class,
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

    /**
     * 获取用户档案（含组织 / 岗位等扩展维度）.
     *
     * <p>用途：auth-service 在签发 Token 时按需补充用户档案维度；office-service 组织同步时使用。
     * 与 {@link #getUserSnapshot} 的区别在于返回更完整的档案信息，调用方按需选择。
     *
     * @param accountId 统一账户 ID
     * @return 用户档案快照
     */
    @GetMapping("/internal/v1/users/{accountId}")
    Result<UserSnapshotDTO> getUserProfile(@PathVariable("accountId") Long accountId);

    /**
     * 获取组织部门树.
     *
     * <p>用途：office-service 组织架构展示、auth-service 数据权限按部门维度计算。
     * 返回递归树结构，根节点为顶层部门。
     *
     * @return 部门树（含多租户过滤，租户 ID 由透传头注入）
     */
    @GetMapping("/internal/v1/depts/tree")
    Result<List<DeptTreeDTO>> getDeptTree();

    /**
     * 按字典类型加载数据字典项.
     *
     * <p>用途：office-service / auth-service 将数据库字典码转换为可读文案。
     * 字典项由提供方管理，消费方应做本地「陈旧并联级」降级缓存。
     *
     * @param type 字典类型编码，例如 {@code sys_user_status}
     * @return 字典项列表
     */
    @GetMapping("/internal/v1/dict/{type}")
    Result<List<DictItemDTO>> getDictItems(@PathVariable("type") String type);
}
