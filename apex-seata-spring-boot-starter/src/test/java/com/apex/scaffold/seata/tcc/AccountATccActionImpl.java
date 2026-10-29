package com.apex.scaffold.seata.tcc;

import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * TCC 真实 TC 验证专用账户 A 动作实现。
 *
 * <p>仅委托 {@link JdbcTccReservation} 完成真实数据库预留/确认/释放，自身不承载业务规则；
 * 以类定义（而非 {@code @Bean} 工厂方法）注册为 Spring Bean：Seata 扫描器只对带有
 * {@code BeanDefinition#getBeanClassName()} 的定义生成动作代理，工厂方法定义的类名为空。
 *
 * @author Apex Team
 */
public class AccountATccActionImpl implements AccountATccAction {
    private final JdbcTccReservation reservation;

    public AccountATccActionImpl(@Qualifier("accountAReservation") JdbcTccReservation reservation) {
        this.reservation = reservation;
    }

    @Override
    public boolean prepare(BusinessActionContext context, int amount) {
        return reservation.prepare(context, amount);
    }

    @Override
    public boolean confirm(BusinessActionContext context) {
        return reservation.confirm(context);
    }

    @Override
    public boolean cancel(BusinessActionContext context) {
        return reservation.cancel(context);
    }
}
