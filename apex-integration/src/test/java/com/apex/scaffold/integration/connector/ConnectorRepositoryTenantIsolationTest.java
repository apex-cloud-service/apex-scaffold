package com.apex.scaffold.integration.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.integration.connector.infrastructure.persistence.mapper.ConnectorConfigMapper;
import com.apex.scaffold.integration.connector.infrastructure.persistence.mapper.CredentialMapper;
import com.apex.scaffold.integration.connector.infrastructure.persistence.po.ConnectorConfigPO;
import com.apex.scaffold.integration.connector.infrastructure.persistence.po.CredentialPO;
import com.apex.scaffold.integration.connector.infrastructure.persistence.repository.ConnectorConfigRepositoryImpl;
import com.apex.scaffold.integration.connector.infrastructure.persistence.repository.CredentialRepositoryImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.test.StepVerifier;

/**
 * 仓储租户隔离反例测试（方案 M5 / 批次 E2）。
 *
 * <p>验证：查询条件显式携带 {@code tenant_id}（叠加行级拦截器）；
 * 按 ID 查询时跨租户行视为不存在（fail-closed）；租户限定删除 0 行以 404 终止。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("仓储租户隔离反例测试")
class ConnectorRepositoryTenantIsolationTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ConnectorConfigPO.class);
        TableInfoHelper.initTableInfo(assistant, CredentialPO.class);
    }

    @Test
    @DisplayName("findActive 查询条件携带 tenant_id 与租户值")
    void findActiveScopedByTenant() {
        ConnectorConfigMapper mapper = mock(ConnectorConfigMapper.class);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
        ConnectorConfigRepositoryImpl repository = new ConnectorConfigRepositoryImpl(mapper);

        StepVerifier.create(repository.findActive("tenant-a", "hook-1")).verifyComplete();

        ArgumentCaptor<Wrapper<ConnectorConfigPO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectOne(captor.capture());
        com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> wrapper =
                (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) captor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("tenant_id");
        assertThat(wrapper.getParamNameValuePairs().values()).contains("tenant-a", "hook-1");
    }

    @Test
    @DisplayName("按 ID 查询命中其他租户行时视为不存在（连接器配置）")
    void findByIdCrossTenantConfigIsEmpty() {
        ConnectorConfigMapper mapper = mock(ConnectorConfigMapper.class);
        ConnectorConfigPO other = new ConnectorConfigPO();
        other.setId(9L);
        other.setTenantId("tenant-b");
        when(mapper.selectById(9L)).thenReturn(other);
        ConnectorConfigRepositoryImpl repository = new ConnectorConfigRepositoryImpl(mapper);

        StepVerifier.create(repository.findById("tenant-a", 9L)).verifyComplete();
    }

    @Test
    @DisplayName("按 ID 查询命中其他租户行时视为不存在（凭据）")
    void findByIdCrossTenantCredentialIsEmpty() {
        CredentialMapper mapper = mock(CredentialMapper.class);
        CredentialPO other = new CredentialPO();
        other.setId(9L);
        other.setTenantId("tenant-b");
        when(mapper.selectById(9L)).thenReturn(other);
        CredentialRepositoryImpl repository = new CredentialRepositoryImpl(mapper);

        StepVerifier.create(repository.findById("tenant-a", 9L)).verifyComplete();
    }

    @Test
    @DisplayName("删除 0 行（含跨租户）以 NOT_FOUND 终止")
    void deleteZeroRowsFails() {
        ConnectorConfigMapper mapper = mock(ConnectorConfigMapper.class);
        when(mapper.delete(any(Wrapper.class))).thenReturn(0);
        ConnectorConfigRepositoryImpl repository = new ConnectorConfigRepositoryImpl(mapper);

        StepVerifier.create(repository.delete("tenant-a", 9L))
                .expectError(BusinessException.class)
                .verify();
        ArgumentCaptor<Wrapper<ConnectorConfigPO>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).delete(captor.capture());
        com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> wrapper =
                (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) captor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("tenant_id");
    }
}
