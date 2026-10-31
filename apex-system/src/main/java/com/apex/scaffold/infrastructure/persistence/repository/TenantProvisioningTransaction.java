package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.infrastructure.persistence.mapper.DeptMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.PermissionMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.RoleMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.RolePermissionMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.TenantMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.UserMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.UserRoleMapper;
import com.apex.scaffold.infrastructure.persistence.po.DeptPO;
import com.apex.scaffold.infrastructure.persistence.po.PermissionPO;
import com.apex.scaffold.infrastructure.persistence.po.RolePO;
import com.apex.scaffold.infrastructure.persistence.po.RolePermissionPO;
import com.apex.scaffold.infrastructure.persistence.po.TenantPO;
import com.apex.scaffold.infrastructure.persistence.po.UserPO;
import com.apex.scaffold.infrastructure.persistence.po.UserRolePO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

/**
 * 租户开通事务（方案批次 B4）。
 *
 * <p>单事务内完成开通引导：默认部门 → 租户管理员账号 → 租户管理员角色 →
 * 按套餐权限码建立角色授权 → 管理员角色绑定 → 租户管理员回填。
 * 任一步失败整体回滚，不残留半开通状态。租户上下文以新租户编码恢复，
 * 使拦截器为各 INSERT 补齐新租户的 {@code tenant_id}。
 *
 * <p>角色授权范围：套餐 {@code permissionCodes} 非空时按套餐授权；无套餐或全量套餐
 * 时授予全部平台权限码（与 platform-full 语义一致）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantProvisioningTransaction implements com.apex.scaffold.domain.gateway.TenantProvisioningGateway {

    /** 内置租户管理员角色编码 */
    public static final String TENANT_ADMIN_ROLE_CODE = "tenant_admin";

    private final TenantMapper tenantMapper;
    private final DeptMapper deptMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final PermissionMapper permissionMapper;
    private final com.apex.scaffold.infrastructure.persistence.mapper.TenantPackageMapper tenantPackageMapper;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    /**
     * 执行开通事务（阻塞 JDBC 于专用池；任一步失败整体回滚并映射业务异常）。
     *
     * @param request 开通参数
     * @return 管理员用户 ID（回填至 sys_tenant.admin_user_id）
     */
    @Override
    public Mono<Long> provision(ProvisioningRequest request) {
        return Mono.fromCallable(() -> {
                    String previousTenantId = TenantContext.setTenantId(request.tenantCode());
                    try {
                        Long adminUserId = transactionTemplate.execute(status -> provisionInternal(request));
                        if (adminUserId == null) {
                            throw new BusinessException(ResultCode.ERROR, "租户开通失败");
                        }
                        return adminUserId;
                    } finally {
                        TenantContext.restoreTenantId(previousTenantId);
                    }
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .doOnSuccess(userId -> log.info("租户开通完成: tenant={}, adminUserId={}", request.tenantCode(), userId));
    }

    private Long provisionInternal(ProvisioningRequest request) {
        String tenantCode = request.tenantCode();

        // 1. 默认部门
        DeptPO dept = new DeptPO();
        dept.setTenantId(tenantCode);
        dept.setParentId(0L);
        dept.setAncestors("0");
        dept.setName(request.tenantName() + "总部");
        dept.setOrderNum(0);
        dept.setStatus(1);
        deptMapper.insert(dept);

        // 2. 租户管理员账号
        UserPO admin = new UserPO();
        admin.setTenantId(tenantCode);
        admin.setUsername(request.adminUsername());
        admin.setPassword(passwordEncoder.encode(request.adminPassword()));
        admin.setNickName("租户管理员");
        admin.setDeptId(dept.getId());
        admin.setStatus(1);
        userMapper.insert(admin);

        // 3. 租户管理员角色（tenant_admin，租户内唯一）
        RolePO role = new RolePO();
        role.setName("租户管理员");
        role.setCode(TENANT_ADMIN_ROLE_CODE);
        role.setDescription("租户开通内置管理员角色");
        role.setStatus(1);
        roleMapper.insert(role);

        // 4. 按套餐权限码建立角色授权（无套餐/全量套餐 → 全部平台权限码）
        for (PermissionPO permission : resolveGrantedPermissions(request.packageCode())) {
            RolePermissionPO rp = new RolePermissionPO();
            rp.setRoleId(role.getId());
            rp.setPermissionId(permission.getId());
            rolePermissionMapper.insert(rp);
        }

        // 5. 管理员角色绑定
        UserRolePO userRole = new UserRolePO();
        userRole.setTenantId(tenantCode);
        userRole.setUserId(admin.getId());
        userRole.setRoleId(role.getId());
        userRoleMapper.insert(userRole);

        // 6. 租户登记与管理员回填
        TenantPO tenant = new TenantPO();
        tenant.setTenantCode(tenantCode);
        tenant.setTenantName(request.tenantName());
        tenant.setAdminUserId(String.valueOf(admin.getId()));
        tenant.setPackageCode(request.packageCode());
        tenant.setMaxUsers(request.maxUsers());
        tenant.setMaxStorageMb(request.maxStorageMb());
        tenant.setRemark(request.remark());
        tenant.setStatus("ACTIVE");
        tenant.setVersion(0);
        tenant.setDeleted(0);
        tenant.setCreateTime(java.time.LocalDateTime.now());
        tenant.setUpdateTime(java.time.LocalDateTime.now());
        tenantMapper.insert(tenant);

        return admin.getId();
    }

    /**
     * 解析应授予的权限记录：套餐 permissionCodes 非空时按套餐过滤；
     * 无套餐/套餐未声明权限码（全量语义）→ 全部平台权限码。
     */
    private List<PermissionPO> resolveGrantedPermissions(String packageCode) {
        QueryWrapper<PermissionPO> query = new QueryWrapper<PermissionPO>().eq("deleted", 0);
        Set<String> grantedCodes = grantedCodesFromPackage(packageCode);
        if (grantedCodes != null && !grantedCodes.isEmpty()) {
            query.in("code", grantedCodes);
        }
        return permissionMapper.selectList(query);
    }

    /**
     * 读取套餐声明的权限码集合；无套餐或解析为空时返回 null（全量语义）。
     */
    private Set<String> grantedCodesFromPackage(String packageCode) {
        if (packageCode == null || packageCode.isBlank()) {
            return null;
        }
        var po = tenantPackageMapper.selectOne(
                new QueryWrapper<com.apex.scaffold.infrastructure.persistence.po.TenantPackagePO>()
                        .eq("package_code", packageCode)
                        .eq("deleted", 0));
        if (po == null
                || po.getPermissionCodes() == null
                || po.getPermissionCodes().isBlank()) {
            return null;
        }
        try {
            var codes = JsonUtil.fromJson(
                    po.getPermissionCodes(),
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {});
            return codes == null || codes.isEmpty() ? null : new java.util.HashSet<>(codes);
        } catch (RuntimeException e) {
            log.warn("套餐权限码解析失败，按全量授权兜底: package={}, reason={}", packageCode, e.getMessage());
            return null;
        }
    }
}
