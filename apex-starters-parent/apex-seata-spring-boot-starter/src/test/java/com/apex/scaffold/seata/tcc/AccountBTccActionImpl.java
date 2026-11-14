package com.apex.scaffold.seata.tcc;

import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * TCC 真实 TC 验证专用账户 B 动作实现。
 *
 * <p>与 {@link AccountATccActionImpl} 独立声明，以类定义注册为不同 Bean，
 * 避免 Seata TCCResource 因相同 actionName 互相覆盖。
 *
 * @author Apex Team
 */
public class AccountBTccActionImpl implements AccountBTccAction {
    private final JdbcTccReservation reservation;

    public AccountBTccActionImpl(@Qualifier("accountBReservation") JdbcTccReservation reservation) {
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
