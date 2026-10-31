package com.apex.scaffold.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.infrastructure.persistence.mapper.DeptMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.PermissionMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.RoleMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.RolePermissionMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.TenantMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.TenantPackageMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.UserMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.UserRoleMapper;
import com.apex.scaffold.infrastructure.persistence.po.PermissionPO;
import com.apex.scaffold.infrastructure.persistence.po.TenantPackagePO;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.test.StepVerifier;

/**
 * 租户开通事务测试（方案批次 B4）。
 *
 * <p>覆盖：开通步骤序列（部门→管理员→角色→授权→绑定→回填）、套餐权限码过滤、
 * 事务回滚传播、编码冲突映射。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("租户开通事务测试")
class TenantProvisioningTransactionTest {

    private final TenantMapper tenantMapper = mock(TenantMapper.class);
    private final DeptMapper deptMapper = mock(DeptMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final RolePermissionMapper rolePermissionMapper = mock(RolePermissionMapper.class);
    private final UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
    private final PermissionMapper permissionMapper = mock(PermissionMapper.class);
    private final TenantPackageMapper tenantPackageMapper = mock(TenantPackageMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

    private TenantProvisioningTransaction transaction;

    @BeforeEach
    void setUp() {
        transaction = new TenantProvisioningTransaction(
                tenantMapper,
                deptMapper,
                userMapper,
                roleMapper,
                rolePermissionMapper,
                userRoleMapper,
                permissionMapper,
                tenantPackageMapper,
                passwordEncoder,
                transactionTemplate);
        // 让事务回调真实执行（内存内按序调用各 mapper）
        when(transactionTemplate.execute(any()))
                .thenAnswer(inv -> ((TransactionCallback<Long>) inv.getArgument(0)).doInTransaction(null));
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-hash");
    }

    private void stubIdPopulation() {
        when(deptMapper.insert(any(com.apex.scaffold.infrastructure.persistence.po.DeptPO.class)))
                .thenAnswer(inv -> {
                    inv.getArgument(0, com.apex.scaffold.infrastructure.persistence.po.DeptPO.class)
                            .setId(10L);
                    return 1;
                });
        when(userMapper.insert(any(com.apex.scaffold.infrastructure.persistence.po.UserPO.class)))
                .thenAnswer(inv -> {
                    inv.getArgument(0, com.apex.scaffold.infrastructure.persistence.po.UserPO.class)
                            .setId(20L);
                    return 1;
                });
        when(roleMapper.insert(any(com.apex.scaffold.infrastructure.persistence.po.RolePO.class)))
                .thenAnswer(inv -> {
                    inv.getArgument(0, com.apex.scaffold.infrastructure.persistence.po.RolePO.class)
                            .setId(30L);
                    return 1;
                });
    }

    private TenantProvisioningTransaction.ProvisioningRequest request() {
        return new com.apex.scaffold.domain.gateway.TenantProvisioningGateway.ProvisioningRequest(
                "tenant-x", "租户X", "x-admin", "secret-123", "basic", 50, 10240, "备注");
    }

    @Test
    @DisplayName("开通步骤序列完整且租户回填管理员 ID")
    void provisionsAllStepsAndBackfillsAdmin() {
        stubIdPopulation();
        when(tenantMapper.insert(any(com.apex.scaffold.infrastructure.persistence.po.TenantPO.class)))
                .thenReturn(1);

        StepVerifier.create(transaction.provision(request()))
                .assertNext(adminUserId -> {
                    assertThat(adminUserId).isEqualTo(20L);
                })
                .verifyComplete();

        ArgumentCaptor<com.apex.scaffold.infrastructure.persistence.po.TenantPO> tenantCaptor =
                ArgumentCaptor.forClass(com.apex.scaffold.infrastructure.persistence.po.TenantPO.class);
        verify(tenantMapper).insert(tenantCaptor.capture());
        assertThat(tenantCaptor.getValue().getTenantCode()).isEqualTo("tenant-x");
        assertThat(tenantCaptor.getValue().getAdminUserId()).isEqualTo("20");
        assertThat(tenantCaptor.getValue().getPackageCode()).isEqualTo("basic");

        ArgumentCaptor<com.apex.scaffold.infrastructure.persistence.po.UserPO> userCaptor =
                ArgumentCaptor.forClass(com.apex.scaffold.infrastructure.persistence.po.UserPO.class);
        verify(userMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("x-admin");
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("bcrypt-hash");
        assertThat(userCaptor.getValue().getTenantId()).isEqualTo("tenant-x");
    }

    @Test
    @DisplayName("套餐权限码非空时按套餐过滤授权")
    void grantsByPackageCodes() {
        stubIdPopulation();
        when(tenantMapper.insert(any(com.apex.scaffold.infrastructure.persistence.po.TenantPO.class)))
                .thenReturn(1);
        TenantPackagePO pkg = new TenantPackagePO();
        pkg.setPermissionCodes("[\"user:read\",\"user:write\"]");
        when(tenantPackageMapper.selectOne(any())).thenReturn(pkg);
        PermissionPO read = new PermissionPO();
        read.setId(101L);
        read.setCode("user:read");
        PermissionPO write = new PermissionPO();
        write.setId(102L);
        write.setCode("user:write");
        when(permissionMapper.selectList(any())).thenReturn(List.of(read, write));

        StepVerifier.create(transaction.provision(request())).expectNext(20L).verifyComplete();

        var captor = ArgumentCaptor.forClass(com.apex.scaffold.infrastructure.persistence.po.RolePermissionPO.class);
        org.mockito.Mockito.verify(rolePermissionMapper, org.mockito.Mockito.times(2))
                .insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(com.apex.scaffold.infrastructure.persistence.po.RolePermissionPO::getPermissionId)
                .containsExactly(101L, 102L);
    }

    @Test
    @DisplayName("事务内失败整体回滚并向上传播异常")
    void propagatesFailureForRollback() {
        stubIdPopulation();
        when(userRoleMapper.insert(any(com.apex.scaffold.infrastructure.persistence.po.UserRolePO.class)))
                .thenThrow(new IllegalStateException("user_role insert failed"));

        StepVerifier.create(transaction.provision(request()))
                .expectError(IllegalStateException.class)
                .verify();
        // 租户登记位于最后一步，失败时不应到达
        org.mockito.Mockito.verify(tenantMapper, org.mockito.Mockito.never())
                .insert(any(com.apex.scaffold.infrastructure.persistence.po.TenantPO.class));
    }
}
