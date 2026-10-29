package com.apex.scaffold.seata.tcc;

import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.BusinessActionContextParameter;
import org.apache.seata.rm.tcc.api.LocalTCC;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;

/**
 * TCC 真实 TC 验证专用业务动作：账户 A 余额预留/确认/取消。
 *
 * <p>仅用于 {@code SeataTccIntegrationTest}，验证 {@code GlobalTransactionScanner} 真实代理
 * {@code @LocalTCC} 接口并在全局提交/回滚时调用二阶段方法，不代表生产业务契约。
 *
 * <p>账户 A、B 各自使用独立接口与独立 {@code actionName}（"accountATccAction"/"accountBTccAction"），
 * 因为 Seata 按 {@code actionName} 注册 {@code TCCResource}，两个资源共用同一名称会互相覆盖。
 *
 * @author Apex Team
 */
@LocalTCC
public interface AccountATccAction {

    @TwoPhaseBusinessAction(name = "accountATccAction", commitMethod = "confirm", rollbackMethod = "cancel")
    boolean prepare(BusinessActionContext context, @BusinessActionContextParameter(paramName = "amount") int amount);

    boolean confirm(BusinessActionContext context);

    boolean cancel(BusinessActionContext context);
}
