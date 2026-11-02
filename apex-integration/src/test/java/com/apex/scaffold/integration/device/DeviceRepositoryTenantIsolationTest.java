package com.apex.scaffold.integration.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.integration.device.infrastructure.persistence.mapper.DeviceCommandMapper;
import com.apex.scaffold.integration.device.infrastructure.persistence.mapper.DeviceMapper;
import com.apex.scaffold.integration.device.infrastructure.persistence.mapper.DeviceRuleMapper;
import com.apex.scaffold.integration.device.infrastructure.persistence.mapper.DeviceShadowMapper;
import com.apex.scaffold.integration.device.infrastructure.persistence.po.DeviceCommandPO;
import com.apex.scaffold.integration.device.infrastructure.persistence.po.DevicePO;
import com.apex.scaffold.integration.device.infrastructure.persistence.po.DeviceRulePO;
import com.apex.scaffold.integration.device.infrastructure.persistence.po.DeviceShadowPO;
import com.apex.scaffold.integration.device.infrastructure.persistence.po.DeviceTelemetryPO;
import com.apex.scaffold.integration.device.infrastructure.persistence.repository.DeviceCommandRepositoryImpl;
import com.apex.scaffold.integration.device.infrastructure.persistence.repository.DeviceRepositoryImpl;
import com.apex.scaffold.integration.device.infrastructure.persistence.repository.DeviceRuleRepositoryImpl;
import com.apex.scaffold.integration.device.infrastructure.persistence.repository.DeviceShadowRepositoryImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.test.StepVerifier;

/**
 * 设备域仓储租户隔离反例测试（方案 M5 / 批次 E3）。
 *
 * <p>验证：查询/更新条件显式携带 {@code tenant_id}（叠加行级拦截器）；
 * 按 ID 查询命中其他租户行视为不存在（fail-closed）；租户限定删除 0 行以 404 终止；
 * 影子条件更新携带 version 乐观锁条件。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("设备域仓储租户隔离反例测试")
class DeviceRepositoryTenantIsolationTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DevicePO.class);
        TableInfoHelper.initTableInfo(assistant, DeviceShadowPO.class);
        TableInfoHelper.initTableInfo(assistant, DeviceTelemetryPO.class);
        TableInfoHelper.initTableInfo(assistant, DeviceCommandPO.class);
        TableInfoHelper.initTableInfo(assistant, DeviceRulePO.class);
    }

    @Test
    @DisplayName("findByKey 查询条件携带 tenant_id 与设备标识")
    void findByKeyScopedByTenant() {
        DeviceMapper mapper = mock(DeviceMapper.class);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
        DeviceRepositoryImpl repository = new DeviceRepositoryImpl(mapper);

        StepVerifier.create(repository.findByKey("tenant-a", "dev-1")).verifyComplete();

        AbstractWrapper<?, ?, ?> wrapper = captureSelectOne(mapper);
        assertThat(wrapper.getSqlSegment()).contains("tenant_id");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("tenant-a", "dev-1");
    }

    @Test
    @DisplayName("按 ID 查询命中其他租户行时视为不存在")
    void findByIdCrossTenantIsEmpty() {
        DeviceMapper mapper = mock(DeviceMapper.class);
        DevicePO other = new DevicePO();
        other.setId(9L);
        other.setTenantId("tenant-b");
        when(mapper.selectById(9L)).thenReturn(other);
        DeviceRepositoryImpl repository = new DeviceRepositoryImpl(mapper);

        StepVerifier.create(repository.findById("tenant-a", 9L)).verifyComplete();
    }

    @Test
    @DisplayName("删除 0 行（含跨租户）以 NOT_FOUND 终止")
    void deleteZeroRowsFails() {
        DeviceMapper mapper = mock(DeviceMapper.class);
        when(mapper.delete(any(Wrapper.class))).thenReturn(0);
        DeviceRepositoryImpl repository = new DeviceRepositoryImpl(mapper);

        StepVerifier.create(repository.delete("tenant-a", 9L))
                .expectError(BusinessException.class)
                .verify();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<DevicePO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).delete(captor.capture());
        assertThat(((AbstractWrapper<?, ?, ?>) captor.getValue()).getSqlSegment())
                .contains("tenant_id");
    }

    @Test
    @DisplayName("影子 reported 条件更新携带租户、设备与 version 乐观锁条件")
    void shadowUpdateCarriesVersionCondition() {
        DeviceShadowMapper mapper = mock(DeviceShadowMapper.class);
        when(mapper.update(any(), any(Wrapper.class))).thenReturn(1);
        DeviceShadowPO persisted = new DeviceShadowPO();
        persisted.setId(1L);
        persisted.setTenantId("tenant-a");
        persisted.setDeviceKey("dev-1");
        persisted.setVersion(3);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(persisted);
        DeviceShadowRepositoryImpl repository = new DeviceShadowRepositoryImpl(mapper);

        StepVerifier.create(repository.updateReported("tenant-a", "dev-1", "{\"temperature\":71}", 2))
                .assertNext(shadow -> assertThat(shadow.getVersion()).isEqualTo(3))
                .verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<DeviceShadowPO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(any(), captor.capture());
        AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) captor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("tenant_id");
        assertThat(wrapper.getSqlSegment()).contains("version");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("tenant-a", "dev-1", 2, 3);
    }

    @Test
    @DisplayName("影子 reported 更新 0 行抛乐观锁冲突")
    void shadowUpdateZeroRowsConflicts() {
        DeviceShadowMapper mapper = mock(DeviceShadowMapper.class);
        when(mapper.update(any(), any(Wrapper.class))).thenReturn(0);
        DeviceShadowRepositoryImpl repository = new DeviceShadowRepositoryImpl(mapper);

        StepVerifier.create(repository.updateReported("tenant-a", "dev-1", "{\"temperature\":71}", 2))
                .expectError(com.apex.scaffold.integration.device.domain.DeviceShadowConflictException.class)
                .verify();
    }

    @Test
    @DisplayName("命令按业务 ID 查询携带租户条件；跨租户命令不可见")
    void commandFindByScopedByTenant() {
        DeviceCommandMapper mapper = mock(DeviceCommandMapper.class);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
        DeviceCommandRepositoryImpl repository = new DeviceCommandRepositoryImpl(mapper);

        StepVerifier.create(repository.findByCommandId("tenant-a", "cmd-1")).verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<DeviceCommandPO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectOne(captor.capture());
        AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) captor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("tenant_id");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("tenant-a", "cmd-1");
    }

    @Test
    @DisplayName("规则启用查询携带租户条件")
    void ruleFindEnabledScopedByTenant() {
        DeviceRuleMapper mapper = mock(DeviceRuleMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of());
        DeviceRuleRepositoryImpl repository = new DeviceRuleRepositoryImpl(mapper);

        StepVerifier.create(repository.findEnabled("tenant-a"))
                .assertNext(rules -> assertThat(rules).isEmpty())
                .verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<DeviceRulePO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(captor.capture());
        assertThat(((AbstractWrapper<?, ?, ?>) captor.getValue()).getSqlSegment())
                .contains("tenant_id");
    }

    private AbstractWrapper<?, ?, ?> captureSelectOne(DeviceMapper mapper) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<DevicePO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectOne(captor.capture());
        return (AbstractWrapper<?, ?, ?>) captor.getValue();
    }
}
