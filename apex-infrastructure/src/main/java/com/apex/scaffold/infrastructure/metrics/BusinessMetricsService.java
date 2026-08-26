package com.apex.scaffold.infrastructure.metrics;

import com.apex.scaffold.domain.service.BusinessMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 业务关键路径指标记录实现（Micrometer）。
 *
 * <p>仅在业务事务成功提交后由领域/应用层调用，指标数据来自真实业务结果。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
public class BusinessMetricsService implements BusinessMetricsPort {

    private static final String METRIC_CREDIT_CHANGE = "apex.business.credit.change";

    private final MeterRegistry meterRegistry;

    /**
     * 创建业务指标服务。
     *
     * @param meterRegistry Micrometer 指标注册表
     */
    public BusinessMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordCreditChange(String type, long amount) {
        if (type == null || amount <= 0L) {
            return;
        }
        Counter counter = Counter.builder(METRIC_CREDIT_CHANGE)
                .description("信用账户变动次数")
                .tag("type", type)
                .register(meterRegistry);
        counter.increment();
    }
}
