package com.apex.scaffold.infrastructure.persistence.datapermission;

import com.apex.scaffold.common.context.TenantContext;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.StringValue;

/**
 * 多租户行级隔离处理器（TenantLineHandler）。
 *
 * <p>配合 MyBatis-Plus {@code TenantLineInnerInterceptor} 使用，为所有
 * SELECT / INSERT / UPDATE / DELETE 语句自动注入 {@code tenant_id} 过滤条件，
 * 实现 SaaS 级行级租户数据隔离。</p>
 *
 * <h2>租户 ID 来源</h2>
 * <p>优先从 {@link TenantContext#currentTenantId()} 读取当前请求线程的租户 ID：
 * <ul>
 *   <li>正常请求：{@code TenantAspect} 从可信认证身份写入上下文</li>
 *   <li>系统表：按白名单跳过租户条件</li>
 *   <li>上下文为空：直接拒绝执行租户 SQL</li>
 * </ul>
 *
 * <h2>忽略的系统表</h2>
 * <p>以下系统配置级表不参与租户隔离：
 * {@code sys_menu / sys_permission / sys_role / sys_role_permission /
 * sys_dict_type / sys_dict_data / sys_tenant / sys_config / sys_oss /
 * scheduler_job / scheduler_job_log / sys_domain_event_outbox / flyway_schema_history}。这些表要么是平台级元数据，要么由超级管理员统一维护。
 * 若未来新增系统字典类表，应在此补充。</p>
 *
 * <h2>启用开关</h2>
 * <p>由 {@code apex.tenant.enabled=true}（仅 Enterprise 档默认开启）控制
 * {@code TenantLineInnerInterceptor} 是否注册；开关关闭时该 Handler
 * 不被装配，所有 SQL 保持原样，不做租户改写。</p>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see TenantContext
 * @see com.apex.scaffold.interfaces.aspect.TenantAspect
 */
@Slf4j
public class DefaultTenantLineHandler implements TenantLineHandler {

    /** 租户列名（与 V14 迁移及各实体 @TableField 保持一致） */
    public static final String TENANT_ID_COLUMN = "tenant_id";

    /** 忽略租户隔离的系统表（全小写，MyBatis-Plus 归一化后匹配） */
    private static final Set<String> IGNORE_TABLES = Set.of(
            "sys_menu",
            "sys_permission",
            "sys_role",
            "sys_role_permission",
            "sys_dict",
            "sys_dict_item",
            "sys_dict_type",
            "sys_dict_data",
            "sys_tenant",
            "sys_config",
            "sys_oss",
            "flyway_schema_history",
            "scheduler_job",
            "scheduler_job_log",
            "sys_domain_event_outbox");

    @Override
    public net.sf.jsqlparser.expression.Expression getTenantId() {
        String tenantId = TenantContext.currentTenantId().orElse(null);
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("租户上下文未装配，拒绝执行租户 SQL");
        }
        return new StringValue(tenantId);
    }

    @Override
    public boolean ignoreTable(String tableName) {
        if (tableName == null) {
            return false;
        }
        boolean ignored = IGNORE_TABLES.contains(tableName.toLowerCase());
        if (log.isTraceEnabled()) {
            log.trace("TenantLineHandler ignoreTable={}, table={}", ignored, tableName);
        }
        return ignored;
    }

    @Override
    public String getTenantIdColumn() {
        return TENANT_ID_COLUMN;
    }
}
