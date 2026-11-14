package com.apex.scaffold.application.service;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 租户写链路守卫。
 *
 * <p>为业务写用例提供统一的租户状态与配额前置断言，避免每个写入口重复
 * “读取租户上下文 + 调用 {@link TenantApplicationService} 断言” 的样板代码。
 * 租户编码取自 {@link TenantContext}（值即租户编码），不接受客户端传入。</p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>断言为独立只读查询，必须作为写操作前的 {@code Mono} 前置步骤，
 *       不得在响应式链中阻塞等待</li>
 *   <li>断言与后续写入不在同一事务内，属“先检后写”；配额按软上限处理</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.4.1
 */
@Service
@RequiredArgsConstructor
public class TenantGuard {

    private final TenantApplicationService tenantApplicationService;

    private final UserRepository userRepository;

    /**
     * 断言当前租户存在且允许创建业务数据。
     *
     * @throws BusinessException 租户上下文缺失（{@code FORBIDDEN}）、租户不存在
     *     或状态不允许（{@code TENANT_ILLEGAL_TRANSITION}）
     */
    public Mono<Void> assertActive() {
        return Mono.defer(() -> tenantApplicationService.assertCanCreateBusinessData(requireTenantCode()));
    }

    /**
     * 断言当前租户为平台租户（{@code default}）——平台专属管理入口守卫（方案 B8）。
     *
     * <p>平台租户治理（租户目录/套餐/平台配置）仅允许平台超管在 {@code default}
     * 租户上下文内执行；租户管理员即使持有同名权限码也被拒绝，形成权限码之外的第二道边界。
     *
     * @throws BusinessException 租户上下文缺失或非平台租户（{@code FORBIDDEN}）
     */
    public Mono<Void> assertPlatformTenant() {
        return Mono.defer(() -> {
            String tenantCode = requireTenantCode();
            if (!"default".equals(tenantCode)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "该操作仅限平台租户执行");
            }
            return Mono.empty();
        });
    }

    /**
     * 断言当前租户状态允许创建，且新增 {@code increment} 个用户后不超出配额。
     *
     * @param increment 本次新增用户数
     * @throws BusinessException 租户上下文缺失、租户不可写或配额超限
     */
    public Mono<Void> assertUserQuota(long increment) {
        return Mono.defer(() -> {
            String tenantCode = requireTenantCode();
            return userRepository
                    .countByCurrentTenant()
                    .flatMap(current -> tenantApplicationService.assertUserQuota(tenantCode, current, increment));
        });
    }

    /**
     * 读取当前租户编码，缺失时拒绝。
     *
     * @return 租户编码
     */
    private String requireTenantCode() {
        return TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
    }
}
