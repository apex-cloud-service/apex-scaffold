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
@lombok.RequiredArgsConstructor
public class BusinessMetricsService implements BusinessMetricsPort {

    private static final String METRIC_CREDIT_CHANGE = "apex.business.credit.change";

    private final MeterRegistry meterRegistry;

    /**
     * 为一次有效的信用变动递增计数器，调用方应在业务事务成功提交后调用。
     *
     * <p>计数器按 {@code type} 区分且每次仅增加一，不累加变动金额；本方法不验证事务状态。
     * 类型应使用有限的业务枚举值，避免将用户或租户标识作为高基数标签。
     *
     * @param type 变动类型，{@code null} 时忽略本次记录
     * @param amount 变动数量，仅用于判断是否大于零，非正数时忽略
     */
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
