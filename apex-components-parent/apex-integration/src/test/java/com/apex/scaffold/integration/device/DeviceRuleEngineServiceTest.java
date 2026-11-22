package com.apex.scaffold.integration.device;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.domain.service.DomainEventPublisher;
import com.apex.scaffold.integration.device.application.DeviceRuleEngineService;
import com.apex.scaffold.integration.device.domain.DeviceRule;
import com.apex.scaffold.integration.device.domain.event.DeviceRuleTriggered;
import com.apex.scaffold.integration.device.domain.repository.DeviceRuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 设备规则条件解析失败隔离测试。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
class DeviceRuleEngineServiceTest {

    private DeviceRuleRepository repository;

    private DomainEventPublisher publisher;

    private DeviceRuleEngineService service;

    /** 初始化真实解析器及规则引擎，外部仓储与事件发布器使用替身。 */
    @BeforeEach
    void setUp() {
        repository = mock(DeviceRuleRepository.class);
        publisher = mock(DomainEventPublisher.class);
        service = new DeviceRuleEngineService(repository, null, null, publisher, new ObjectMapper());
    }

    /** 非法条件必须跳过动作，遥测评估正常完成。 */
    @Test
    void malformedConditionDoesNotTriggerAction() {
        when(repository.findEnabled("t1")).thenReturn(Mono.just(List.of(rule(1L, "{invalid"))));

        StepVerifier.create(service.evaluate("t1", "device-1", "{\"temperature\":30}"))
                .verifyComplete();

        verifyNoInteractions(publisher);
    }

    /** 坏规则不得阻断后续有效规则，且只发布有效规则事件。 */
    @Test
    void malformedConditionDoesNotPreventFollowingValidRule() {
        when(repository.findEnabled("t1"))
                .thenReturn(Mono.just(List.of(
                        rule(1L, "{invalid"), rule(2L, "{\"field\":\"temperature\",\"op\":\"GT\",\"value\":20}"))));
        when(publisher.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.evaluate("t1", "device-1", "{\"temperature\":30}"))
                .verifyComplete();

        verify(publisher)
                .publish(org.mockito.ArgumentMatchers.<DeviceRuleTriggered>argThat(event -> event.getRuleId() == 2L));
        verifyNoMoreInteractions(publisher);
    }

    /** 空条件数组被解析器拒绝，必须跳过动作。 */
    @Test
    void emptyConditionsDoNotTriggerAction() {
        when(repository.findEnabled("t1")).thenReturn(Mono.just(List.of(rule(1L, "[]"))));

        StepVerifier.create(service.evaluate("t1", "device-1", "{}")).verifyComplete();

        verifyNoInteractions(publisher);
    }

    /**
     * 创建事件动作规则。
     *
     * @param id 规则标识
     * @param conditionJson 条件 JSON
     * @return 待评估规则
     */
    private DeviceRule rule(Long id, String conditionJson) {
        DeviceRule rule = new DeviceRule();
        rule.setId(id);
        rule.setTenantId("t1");
        rule.setRuleName("rule-" + id);
        rule.setConditionJson(conditionJson);
        rule.setActionType(DeviceRule.ACTION_EVENT);
        return rule;
    }
}
