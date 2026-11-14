package com.apex.scaffold.interfaces.governance.lifecycle;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Component;

/**
 * Spring readiness 状态适配器。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
public class ReadinessStateController implements ApplicationEventPublisherAware {
    private final ApplicationAvailability availability;
    private final AtomicBoolean accepting = new AtomicBoolean(false);
    private ApplicationEventPublisher eventPublisher;

    /**
     * 创建 readiness 控制器。
     *
     * @param availability Spring 应用可用性状态
     */
    public ReadinessStateController(ApplicationAvailability availability) {
        this.availability = availability;
    }

    /**
     * 发布可以接收流量状态。
     */
    public void acceptTraffic() {
        if (accepting.compareAndSet(false, true)) {
            publish(ReadinessState.ACCEPTING_TRAFFIC);
        }
    }

    /**
     * 发布拒绝流量状态。
     */
    public void refuseTraffic() {
        if (accepting.compareAndSet(true, false)) {
            publish(ReadinessState.REFUSING_TRAFFIC);
        }
    }

    /**
     * 返回当前是否允许流量。
     *
     * @return 是否允许流量
     */
    public boolean isAcceptingTraffic() {
        return accepting.get();
    }

    /**
     * 接收 Spring 事件发布器。
     *
     * @param publisher 事件发布器
     */
    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
        this.eventPublisher = publisher;
    }

    private void publish(ReadinessState state) {
        if (eventPublisher != null) {
            AvailabilityChangeEvent.publish(eventPublisher, this, state);
        }
    }
}
