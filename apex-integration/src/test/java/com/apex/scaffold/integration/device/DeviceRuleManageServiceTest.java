package com.apex.scaffold.integration.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.device.application.DeviceRuleManageService;
import com.apex.scaffold.integration.device.domain.DeviceRule;
import com.apex.scaffold.integration.device.domain.repository.DeviceRuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 轻量规则管理用例测试（方案 M5 / 批次 E3）。
 *
 * <p>覆盖：规则名与动作白名单校验、条件 JSON 结构化预检、启停与详情的不存在判定。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("轻量规则管理用例测试")
class DeviceRuleManageServiceTest {

    private static final String VALID_CONDITION = "{\"field\":\"temperature\",\"op\":\">\",\"value\":30}";

    private DeviceRuleRepository repository;

    private DeviceRuleManageService service;

    @BeforeEach
    void setUp() {
        repository = mock(DeviceRuleRepository.class);
        service = new DeviceRuleManageService(repository, new ObjectMapper());
    }

    @Test
    @DisplayName("规则名：空、空白或超长均拒绝")
    void createRejectsInvalidRuleName() {
        StepVerifier.create(service.create("t1", null, VALID_CONDITION, DeviceRule.ACTION_NOTIFY, null, true))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(service.create("t1", "  ", VALID_CONDITION, DeviceRule.ACTION_NOTIFY, null, true))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(service.create("t1", "x".repeat(65), VALID_CONDITION, DeviceRule.ACTION_NOTIFY, null, true))
                .expectError(BusinessException.class)
                .verify();

        verify(repository, never()).insert(any(DeviceRule.class));
    }

    @Test
    @DisplayName("动作类型：空或不在白名单拒绝")
    void createRejectsUnsupportedAction() {
        StepVerifier.create(service.create("t1", "高温告警", VALID_CONDITION, null, null, true))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(service.create("t1", "高温告警", VALID_CONDITION, "SMS", null, true))
                .expectError(BusinessException.class)
                .verify();

        verify(repository, never()).insert(any(DeviceRule.class));
    }

    @Test
    @DisplayName("条件 JSON：非法或结构不符在落库前拒绝")
    void createRejectsInvalidCondition() {
        StepVerifier.create(service.create("t1", "高温告警", "not-json", DeviceRule.ACTION_NOTIFY, null, true))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(service.create("t1", "高温告警", "[]", DeviceRule.ACTION_NOTIFY, null, true))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(service.create("t1", "高温告警", "[1]", DeviceRule.ACTION_NOTIFY, null, true))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(service.create(
                        "t1", "高温告警", "{\"field\":\"temperature\"}", DeviceRule.ACTION_NOTIFY, null, true))
                .expectError(BusinessException.class)
                .verify();

        verify(repository, never()).insert(any(DeviceRule.class));
    }

    @Test
    @DisplayName("创建：启用状态按入参，规则名去除空白")
    void createPersistsRule() {
        when(repository.insert(any(DeviceRule.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.create("t1", " 高温告警 ", VALID_CONDITION, DeviceRule.ACTION_NOTIFY, null, true))
                .assertNext(rule -> {
                    assertThat(rule.getTenantId()).isEqualTo("t1");
                    assertThat(rule.getRuleName()).isEqualTo("高温告警");
                    assertThat(rule.getConditionJson()).isEqualTo(VALID_CONDITION);
                    assertThat(rule.getActionType()).isEqualTo(DeviceRule.ACTION_NOTIFY);
                    assertThat(rule.getEnabled()).isEqualTo(1);
                })
                .verifyComplete();

        ArgumentCaptor<DeviceRule> captor = ArgumentCaptor.forClass(DeviceRule.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().getActionConfig()).isNull();
    }

    @Test
    @DisplayName("创建：禁用状态落 0")
    void createPersistsDisabledRule() {
        when(repository.insert(any(DeviceRule.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.create("t1", "规则", VALID_CONDITION, DeviceRule.ACTION_WEBHOOK, "{}", false))
                .assertNext(rule -> assertThat(rule.getEnabled()).isZero())
                .verifyComplete();
    }

    @Test
    @DisplayName("启停：更新 0 行视为不存在")
    void toggleMissingRuleIsNotFound() {
        when(repository.updateEnabled("t1", 9L, false)).thenReturn(Mono.just(false));

        StepVerifier.create(service.toggle("t1", 9L, false))
                .expectErrorSatisfies(error ->
                        assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()))
                .verify();
    }

    @Test
    @DisplayName("启停：更新成功完成")
    void toggleExistingRule() {
        when(repository.updateEnabled("t1", 1L, true)).thenReturn(Mono.just(true));

        StepVerifier.create(service.toggle("t1", 1L, true)).verifyComplete();
    }

    @Test
    @DisplayName("详情：不存在返回 404")
    void getMissingRuleIsNotFound() {
        when(repository.findById("t1", 9L)).thenReturn(Mono.empty());

        StepVerifier.create(service.get("t1", 9L))
                .expectErrorSatisfies(error ->
                        assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()))
                .verify();
    }

    @Test
    @DisplayName("详情：命中返回规则")
    void getExistingRule() {
        DeviceRule rule = new DeviceRule();
        rule.setId(1L);
        rule.setTenantId("t1");
        when(repository.findById("t1", 1L)).thenReturn(Mono.just(rule));

        StepVerifier.create(service.get("t1", 1L))
                .assertNext(result -> assertThat(result.getId()).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    @DisplayName("列表：透传关键字、启停过滤与分页参数")
    void pagePassesFiltersThrough() {
        DeviceRule rule = new DeviceRule();
        rule.setId(2L);
        rule.setTenantId("t1");
        com.apex.scaffold.common.dto.PageResponse<DeviceRule> page =
                com.apex.scaffold.common.dto.PageResponse.of(java.util.List.of(rule), 1L, 2, 10);
        when(repository.page("t1", "温度", true, 2, 10)).thenReturn(Mono.just(page));

        StepVerifier.create(service.page("t1", "温度", true, 2, 10))
                .assertNext(result -> {
                    assertThat(result.getTotal()).isEqualTo(1L);
                    assertThat(result.getList()).extracting(DeviceRule::getId).containsExactly(2L);
                })
                .verifyComplete();

        verify(repository).page("t1", "温度", true, 2, 10);
    }
}
