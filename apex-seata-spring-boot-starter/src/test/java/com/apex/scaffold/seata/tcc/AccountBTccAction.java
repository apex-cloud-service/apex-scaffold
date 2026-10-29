package com.apex.scaffold.seata.tcc;

import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.BusinessActionContextParameter;
import org.apache.seata.rm.tcc.api.LocalTCC;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;

/**
 * TCC 真实 TC 验证专用业务动作：账户 B 余额预留/确认/取消。
 *
 * <p>与 {@link AccountATccAction} 同构但独立声明，使用不同的 {@code actionName}
 * （"accountBTccAction"）以避免 Seata {@code TCCResource} 注册冲突。
 *
 * @author Apex Team
 */
@LocalTCC
public interface AccountBTccAction {

    @TwoPhaseBusinessAction(name = "accountBTccAction", commitMethod = "confirm", rollbackMethod = "cancel")
    boolean prepare(BusinessActionContext context, @BusinessActionContextParameter(paramName = "amount") int amount);

    boolean confirm(BusinessActionContext context);

    boolean cancel(BusinessActionContext context);
}
