package com.apex.scaffold.integration.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.connector.application.ConnectorConfigManageService;
import com.apex.scaffold.integration.connector.domain.ConnectorDefinition;
import com.apex.scaffold.integration.connector.domain.repository.ConnectorConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 连接器配置管理用例测试（方案 M5 / 批次 E2）。
 *
 * <p>覆盖：分页与详情委托、创建前 JSON 合法性校验与默认启用、更新时未变更字段
 * 保留原值、乐观锁版本透传、跨租户详情视为不存在。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("连接器配置管理用例测试")
class ConnectorConfigManageServiceTest {

    private ConnectorConfigRepository repository;

    private ConnectorConfigManageService service;

    @BeforeEach
    void setUp() {
        repository = mock(ConnectorConfigRepository.class);
        service = new ConnectorConfigManageService(repository, new ObjectMapper());
    }

    @Test
    @DisplayName("分页查询委托仓储")
    void pageDelegatesToRepository() {
        ConnectorDefinition definition = definition(1L, "HTTP", "cfg-1");
        when(repository.page("t1", "HTTP", 1, 10))
                .thenReturn(Mono.just(PageResponse.of(List.of(definition), 1L, 1, 10)));

        StepVerifier.create(service.page("t1", "HTTP", 1, 10))
                .assertNext(page -> assertThat(page.getTotal()).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    @DisplayName("详情：跨租户或不存在返回 404")
    void getMissingIsNotFound() {
        when(repository.findById("t1", 9L)).thenReturn(Mono.empty());

        StepVerifier.create(service.get("t1", 9L))
                .expectErrorSatisfies(error ->
                        assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()))
                .verify();
    }

    @Test
    @DisplayName("创建：合法 JSON 落库且默认启用、类型与标识去除空白")
    void createPersistsEnabledDefinition() {
        when(repository.insert(any(ConnectorDefinition.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.create("t1", " HTTP ", " cfg-1 ", "{\"endpoint\":\"https://x\"}", "备注"))
                .assertNext(definition -> {
                    assertThat(definition.getTenantId()).isEqualTo("t1");
                    assertThat(definition.getConnectorType()).isEqualTo("HTTP");
                    assertThat(definition.getConfigKey()).isEqualTo("cfg-1");
                    assertThat(definition.getConfigValue()).isEqualTo("{\"endpoint\":\"https://x\"}");
                    assertThat(definition.getStatus()).isEqualTo(1);
                    assertThat(definition.getRemark()).isEqualTo("备注");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("创建：必填缺失或 JSON 非法时拒绝且不落库")
    void createRejectsInvalidInput() {
        StepVerifier.create(service.create("t1", null, "cfg-1", "{}", null))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(service.create("t1", " ", "cfg-1", "{}", null))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(service.create("t1", "HTTP", null, "{}", null))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(service.create("t1", "HTTP", " ", "{}", null))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(service.create("t1", "HTTP", "cfg-1", null, null))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(service.create("t1", "HTTP", "cfg-1", "not-json", null))
                .expectError(BusinessException.class)
                .verify();

        verify(repository, never()).insert(any(ConnectorDefinition.class));
    }

    @Test
    @DisplayName("更新：仅版本变更时保留原配置与备注")
    void updateKeepsValuesWhenNotProvided() {
        ConnectorDefinition existing = definition(1L, "HTTP", "cfg-1");
        existing.setConfigValue("{\"a\":1}");
        existing.setRemark("old");
        when(repository.findById("t1", 1L)).thenReturn(Mono.just(existing));
        when(repository.update(any(ConnectorDefinition.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.update("t1", 1L, null, null, 3))
                .assertNext(updated -> {
                    assertThat(updated.getConfigValue()).isEqualTo("{\"a\":1}");
                    assertThat(updated.getRemark()).isEqualTo("old");
                    assertThat(updated.getVersion()).isEqualTo(3);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("更新：仅备注变更时校验分支走 JSON 校验并替换备注")
    void updateAppliesRemarkOnly() {
        ConnectorDefinition existing = definition(1L, "HTTP", "cfg-1");
        existing.setConfigValue("{\"a\":1}");
        when(repository.findById("t1", 1L)).thenReturn(Mono.just(existing));
        when(repository.update(any(ConnectorDefinition.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.update("t1", 1L, null, "new", 4))
                .assertNext(updated -> {
                    assertThat(updated.getConfigValue()).isEqualTo("{\"a\":1}");
                    assertThat(updated.getRemark()).isEqualTo("new");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("更新：配置 JSON 变更时校验通过后替换")
    void updateAppliesConfigValue() {
        ConnectorDefinition existing = definition(1L, "HTTP", "cfg-1");
        existing.setConfigValue("{\"a\":1}");
        existing.setRemark("old");
        when(repository.findById("t1", 1L)).thenReturn(Mono.just(existing));
        when(repository.update(any(ConnectorDefinition.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.update("t1", 1L, "{\"b\":2}", null, 5))
                .assertNext(updated -> assertThat(updated.getConfigValue()).isEqualTo("{\"b\":2}"))
                .verifyComplete();

        ArgumentCaptor<ConnectorDefinition> captor = ArgumentCaptor.forClass(ConnectorDefinition.class);
        verify(repository).update(captor.capture());
        assertThat(captor.getValue().getRemark()).isEqualTo("old");
        assertThat(captor.getValue().getVersion()).isEqualTo(5);
    }

    @Test
    @DisplayName("更新：不存在返回 404；新 JSON 非法时拒绝且不落库")
    void updateRejectsMissingOrInvalidPayload() {
        when(repository.findById("t1", 1L)).thenReturn(Mono.empty());
        StepVerifier.create(service.update("t1", 1L, "{\"a\":1}", null, 1))
                .expectErrorSatisfies(error ->
                        assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()))
                .verify();

        ConnectorDefinition existing = definition(2L, "HTTP", "cfg-2");
        when(repository.findById("t1", 2L)).thenReturn(Mono.just(existing));
        StepVerifier.create(service.update("t1", 2L, "not-json", null, 1))
                .expectErrorSatisfies(error ->
                        assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode()))
                .verify();

        verify(repository, never()).update(any(ConnectorDefinition.class));
    }

    @Test
    @DisplayName("删除：委托仓储完成信号")
    void deleteDelegatesToRepository() {
        when(repository.delete("t1", 2L)).thenReturn(Mono.empty());

        StepVerifier.create(service.delete("t1", 2L)).verifyComplete();
        verify(repository).delete("t1", 2L);
    }

    private ConnectorDefinition definition(Long id, String connectorType, String configKey) {
        ConnectorDefinition definition = new ConnectorDefinition();
        definition.setId(id);
        definition.setTenantId("t1");
        definition.setConnectorType(connectorType);
        definition.setConfigKey(configKey);
        definition.setConfigValue("{}");
        definition.setStatus(1);
        return definition;
    }
}
