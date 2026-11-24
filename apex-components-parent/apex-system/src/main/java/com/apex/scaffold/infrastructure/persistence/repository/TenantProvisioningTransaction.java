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
import com.apex.scaffold.infrastructure.persistence.po.TenantPackagePO;
import com.apex.scaffold.infrastructure.persistence.po.UserPO;
import com.apex.scaffold.infrastructure.persistence.po.UserRolePO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.LinkedHashSet;
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
 * <p>角色授权范围只来自有效套餐声明的 {@code permissionCodes}。套餐缺失、未知、停用、
 * 逻辑删除或权限配置损坏时，开通在任何 INSERT 前整体失败，禁止回退为全量授权；
 * 内置 {@code platform-full} 套餐同样按其显式权限码授权。
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

        // 0. 在任何写入前完成套餐校验；失败时事务不产生任何半开通数据。
        String packageCode = requireAvailablePackageCode(request.packageCode());
        List<PermissionPO> grantedPermissions = resolveGrantedPermissions(packageCode);

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

        // 4. 按有效套餐声明的权限码建立角色授权
        for (PermissionPO permission : grantedPermissions) {
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
        tenant.setPackageCode(packageCode);
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
     * 校验并返回可用于开通的套餐编码。
     *
     * <p>开通引导必须绑定一个存在、启用且未逻辑删除的套餐。任一条件不满足即抛出
     * {@link BusinessException}，由外层事务回滚，禁止把缺失配置解释为平台全量权限。</p>
     *
     * @param packageCode 请求中的套餐编码
     * @return 去除首尾空白后的套餐编码
     * @throws BusinessException 套餐编码为空、套餐不存在、已停用或已逻辑删除时抛出
     */
    private String requireAvailablePackageCode(String packageCode) {
        if (packageCode == null || packageCode.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "租户套餐不能为空");
        }
        String normalizedCode = packageCode.trim();
        TenantPackagePO pkg = tenantPackageMapper.selectOne(new QueryWrapper<TenantPackagePO>()
                .eq("package_code", normalizedCode)
                .eq("deleted", 0)
                .eq("status", 1));
        if (pkg == null
                || pkg.getDeleted() == null
                || pkg.getDeleted() != 0
                || pkg.getStatus() == null
                || pkg.getStatus() != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "租户套餐不存在或不可用");
        }
        return normalizedCode;
    }

    /**
     * 解析应授予的权限记录。
     *
     * <p>套餐权限码必须是非空 JSON 字符串数组，数组元素不得为 {@code null} 或空白。
     * 解析成功后按权限码查询未删除的权限记录；不提供全量回退路径。</p>
     *
     * @param packageCode 已校验可用的套餐编码
     * @return 与套餐权限码匹配的权限记录，可能为空但绝不为全量回退结果
     * @throws BusinessException 权限码 JSON 损坏或数组为空时抛出
     */
    private List<PermissionPO> resolveGrantedPermissions(String packageCode) {
        TenantPackagePO pkg = tenantPackageMapper.selectOne(new QueryWrapper<TenantPackagePO>()
                .eq("package_code", packageCode)
                .eq("deleted", 0)
                .eq("status", 1));
        if (pkg == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "租户套餐不存在或不可用");
        }
        Set<String> grantedCodes = parsePermissionCodes(pkg.getPermissionCodes());
        return permissionMapper.selectList(
                new QueryWrapper<PermissionPO>().eq("deleted", 0).in("code", grantedCodes));
    }

    /**
     * 严格解析套餐权限码数组。
     *
     * @param json 权限码 JSON 文本
     * @return 去重后的非空白权限码集合
     * @throws BusinessException JSON 格式错误、类型错误、数组为空或元素无效时抛出
     */
    private Set<String> parsePermissionCodes(String json) {
        if (json == null || json.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "租户套餐权限配置损坏");
        }
        try {
            List<String> values = JsonUtil.getObjectMapper()
                    .readValue(
                            json,
                            JsonUtil.getObjectMapper()
                                    .getTypeFactory()
                                    .constructCollectionType(List.class, String.class));
            if (values == null || values.isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "租户套餐权限配置损坏");
            }
            if (values.stream().anyMatch(code -> code == null || code.isBlank())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "租户套餐权限配置损坏");
            }
            Set<String> codes = new LinkedHashSet<>();
            values.forEach(code -> codes.add(code.trim()));
            return codes;
        } catch (BusinessException error) {
            throw error;
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "租户套餐权限配置损坏", error);
        }
    }
}
