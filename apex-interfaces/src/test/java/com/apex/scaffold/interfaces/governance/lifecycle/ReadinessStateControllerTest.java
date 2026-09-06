package com.apex.scaffold.interfaces.governance.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.context.ApplicationEventPublisher;

/**
 * readiness 状态控制器测试。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("ReadinessStateController 单元测试")
class ReadinessStateControllerTest {

    /**
     * 验证 readiness 状态切换会发布 Spring 标准可用性事件。
     */
    @Test
    @DisplayName("状态切换应发布一次可用性事件")
    void shouldPublishAvailabilityEventsOnlyWhenStateChanges() {
        ApplicationAvailability availability = mock(ApplicationAvailability.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ReadinessStateController controller = new ReadinessStateController(availability);
        controller.setApplicationEventPublisher(publisher);

        controller.acceptTraffic();
        controller.acceptTraffic();
        controller.refuseTraffic();
        controller.refuseTraffic();

        assertThat(controller.isAcceptingTraffic()).isFalse();
        verify(publisher, org.mockito.Mockito.times(2))
                .publishEvent(org.mockito.ArgumentMatchers.any(AvailabilityChangeEvent.class));
        verifyNoMoreInteractions(publisher);
    }
}
