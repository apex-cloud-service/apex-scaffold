package com.apex.scaffold.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.infrastructure.persistence.mapper.DeptMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.PermissionMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.RoleMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.RolePermissionMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.TenantMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.TenantPackageMapper;
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
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.test.StepVerifier;

/**
 * 租户开通事务测试（方案批次 B4）。
 *
 * <p>覆盖开通步骤序列、套餐权限码过滤、事务回滚传播、编码冲突映射，以及套餐缺失、
 * 未知、停用、逻辑删除和权限 JSON 损坏时在任何 INSERT 前整体失败的安全边界。</p>
 *
 * @author Apex Team
 * @version 1.3.2
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

    /**
     * 构造被测事务并让事务模板执行真实回调。
     */
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
        when(transactionTemplate.execute(any()))
                .thenAnswer(inv -> ((TransactionCallback<Long>) inv.getArgument(0)).doInTransaction(null));
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-hash");
    }

    /**
     * 模拟主键回填，使开通步骤可以完成。
     */
    private void stubIdPopulation() {
        when(deptMapper.insert(any(DeptPO.class))).thenAnswer(inv -> {
            inv.getArgument(0, DeptPO.class).setId(10L);
            return 1;
        });
        when(userMapper.insert(any(UserPO.class))).thenAnswer(inv -> {
            inv.getArgument(0, UserPO.class).setId(20L);
            return 1;
        });
        when(roleMapper.insert(any(RolePO.class))).thenAnswer(inv -> {
            inv.getArgument(0, RolePO.class).setId(30L);
            return 1;
        });
    }

    /**
     * 构造启用且权限配置有效的套餐。
     *
     * @return 套餐持久化对象
     */
    private TenantPackagePO validPackage() {
        TenantPackagePO pkg = new TenantPackagePO();
        pkg.setPackageCode("basic");
        pkg.setStatus(1);
        pkg.setDeleted(0);
        pkg.setPermissionCodes("[\"user:read\",\"user:write\"]");
        return pkg;
    }

    /**
     * 构造默认开通请求。
     *
     * @return 开通请求
     */
    private TenantProvisioningTransaction.ProvisioningRequest request() {
        return new com.apex.scaffold.domain.gateway.TenantProvisioningGateway.ProvisioningRequest(
                "tenant-x", "租户X", "x-admin", "secret-123", "basic", 50, 10240, "备注");
    }

    /**
     * 构造指定套餐编码的开通请求。
     *
     * @param packageCode 套餐编码
     * @return 开通请求
     */
    private TenantProvisioningTransaction.ProvisioningRequest request(String packageCode) {
        return new com.apex.scaffold.domain.gateway.TenantProvisioningGateway.ProvisioningRequest(
                "tenant-x", "租户X", "x-admin", "secret-123", packageCode, 50, 10240, "备注");
    }

    /**
     * 验证完整开通步骤和租户管理员回填。
     */
    @Test
    @DisplayName("开通步骤序列完整且租户回填管理员 ID")
    void provisionsAllStepsAndBackfillsAdmin() {
        stubIdPopulation();
        when(tenantPackageMapper.selectOne(any())).thenReturn(validPackage());
        when(permissionMapper.selectList(any())).thenReturn(List.of(permission(101L, "user:read")));
        when(tenantMapper.insert(any(TenantPO.class))).thenReturn(1);

        StepVerifier.create(transaction.provision(request()))
                .assertNext(adminUserId -> assertThat(adminUserId).isEqualTo(20L))
                .verifyComplete();

        ArgumentCaptor<TenantPO> tenantCaptor = ArgumentCaptor.forClass(TenantPO.class);
        verify(tenantMapper).insert(tenantCaptor.capture());
        assertThat(tenantCaptor.getValue().getTenantCode()).isEqualTo("tenant-x");
        assertThat(tenantCaptor.getValue().getAdminUserId()).isEqualTo("20");
        assertThat(tenantCaptor.getValue().getPackageCode()).isEqualTo("basic");

        ArgumentCaptor<UserPO> userCaptor = ArgumentCaptor.forClass(UserPO.class);
        verify(userMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("x-admin");
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("bcrypt-hash");
        assertThat(userCaptor.getValue().getTenantId()).isEqualTo("tenant-x");
    }

    /**
     * 验证套餐权限码非空时严格按套餐过滤授权。
     */
    @Test
    @DisplayName("套餐权限码非空时按套餐过滤授权")
    void grantsByPackageCodes() {
        stubIdPopulation();
        when(tenantPackageMapper.selectOne(any())).thenReturn(validPackage());
        when(tenantMapper.insert(any(TenantPO.class))).thenReturn(1);
        when(permissionMapper.selectList(any()))
                .thenReturn(List.of(permission(101L, "user:read"), permission(102L, "user:write")));

        StepVerifier.create(transaction.provision(request())).expectNext(20L).verifyComplete();

        ArgumentCaptor<RolePermissionPO> captor = ArgumentCaptor.forClass(RolePermissionPO.class);
        verify(rolePermissionMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(RolePermissionPO::getPermissionId)
                .containsExactly(101L, 102L);
    }

    /**
     * 验证事务内失败整体回滚并向上传播异常。
     */
    @Test
    @DisplayName("事务内失败整体回滚并向上传播异常")
    void propagatesFailureForRollback() {
        stubIdPopulation();
        when(tenantPackageMapper.selectOne(any())).thenReturn(validPackage());
        when(permissionMapper.selectList(any())).thenReturn(List.of(permission(101L, "user:read")));
        when(userRoleMapper.insert(any(UserRolePO.class)))
                .thenThrow(new IllegalStateException("user_role insert failed"));

        StepVerifier.create(transaction.provision(request()))
                .expectError(IllegalStateException.class)
                .verify();
        verify(tenantMapper, never()).insert(any(TenantPO.class));
    }

    /**
     * 构造无效套餐输入。
     *
     * @return 参数化测试数据
     */
    static Stream<Arguments> invalidPackages() {
        return Stream.of(
                Arguments.of("空白套餐编码", null, "租户套餐不能为空"),
                Arguments.of("套餐不存在", null, "租户套餐不存在或不可用"),
                Arguments.of("套餐停用", packageWithStatus(0, 0, "[\"user:read\"]"), "租户套餐不存在或不可用"),
                Arguments.of("套餐逻辑删除", packageWithStatus(1, 1, "[\"user:read\"]"), "租户套餐不存在或不可用"),
                Arguments.of("权限 JSON 非数组", packageWithStatus(1, 0, "{\"read\":true}"), "租户套餐权限配置损坏"),
                Arguments.of("权限数组为空", packageWithStatus(1, 0, "[]"), "租户套餐权限配置损坏"),
                Arguments.of("权限数组含 null", packageWithStatus(1, 0, "[\"user:read\",null]"), "租户套餐权限配置损坏"),
                Arguments.of("权限数组含空白", packageWithStatus(1, 0, "[\"user:read\",\" \"]"), "租户套餐权限配置损坏"));
    }

    /**
     * 构造指定状态与权限 JSON 的套餐。
     *
     * @param status 状态
     * @param deleted 逻辑删除标记
     * @param permissionCodes 权限 JSON
     * @return 套餐持久化对象
     */
    private static TenantPackagePO packageWithStatus(int status, int deleted, String permissionCodes) {
        TenantPackagePO pkg = new TenantPackagePO();
        pkg.setPackageCode("basic");
        pkg.setStatus(status);
        pkg.setDeleted(deleted);
        pkg.setPermissionCodes(permissionCodes);
        return pkg;
    }

    /**
     * 构造权限记录。
     *
     * @param id 权限 ID
     * @param code 权限码
     * @return 权限持久化对象
     */
    private static PermissionPO permission(Long id, String code) {
        PermissionPO permission = new PermissionPO();
        permission.setId(id);
        permission.setCode(code);
        return permission;
    }

    /**
     * 验证所有无效套餐均在部门、用户、角色和授权写入前失败。
     *
     * @param scenario 场景名称
     * @param pkg 套餐返回对象；空白编码场景不依赖该值，{@code null} 也可表示查无套餐
     * @param expectedMessage 期望业务异常消息
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPackages")
    @DisplayName("无效套餐在任何 INSERT 前失败")
    void rejectsInvalidPackageBeforeAnyInsert(String scenario, TenantPackagePO pkg, String expectedMessage) {
        if (pkg == null) {
            when(tenantPackageMapper.selectOne(any())).thenReturn(null);
        } else {
            when(tenantPackageMapper.selectOne(any())).thenReturn(pkg);
        }
        String packageCode = "空白套餐编码".equals(scenario) ? "  " : "basic";

        StepVerifier.create(transaction.provision(request(packageCode)))
                .expectErrorMatches(error -> error instanceof BusinessException business
                        && ResultCode.PARAM_ERROR.getCode().equals(business.getCode())
                        && expectedMessage.equals(business.getMsg()))
                .verify();

        verify(deptMapper, never()).insert(any(DeptPO.class));
        verify(userMapper, never()).insert(any(UserPO.class));
        verify(roleMapper, never()).insert(any(RolePO.class));
        verify(rolePermissionMapper, never()).insert(any(RolePermissionPO.class));
        verify(userRoleMapper, never()).insert(any(UserRolePO.class));
        verify(tenantMapper, never()).insert(any(TenantPO.class));
    }
}
