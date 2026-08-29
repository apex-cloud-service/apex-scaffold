package com.apex.scaffold.common.context;

import java.util.Optional;

/**
 * 数据权限装配 SPI。
 *
 * <p>负责解析"当前请求用户是谁、能看哪些数据"，返回 {@link DataScopeInfo}。
 * 拦截器（{@code DataScopeInnerInterceptor}）与切面（{@code DataScopeAspect}）
 * 通过该接口与数据来源解耦（MDC / SecurityContext / 角色数据权限配置等），
 * 便于不同部署形态替换实现。</p>
 *
 * <p>默认实现 {@code MdcDataScopeProvider} 依次尝试：显式装配的
 * {@link DataScopeContext}（ThreadLocal）→ MDC 键（{@code apex.userId} /
 * {@code apex.deptId} / {@code apex.dataScope} / {@code apex.dataScopeDeptIds}）。
 * 返回 {@link Optional#empty()} 表示无法解析（此时查询放行、不做数据权限过滤）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.3.0
 * @see DataScopeInfo
 */
@FunctionalInterface
public interface DataScopeProvider {

    /**
     * 解析当前请求的数据权限信息。
     *
     * @return 数据权限信息；无法解析时返回 {@link Optional#empty()}（放行不拦截）
     */
    Optional<DataScopeInfo> resolve();
}
