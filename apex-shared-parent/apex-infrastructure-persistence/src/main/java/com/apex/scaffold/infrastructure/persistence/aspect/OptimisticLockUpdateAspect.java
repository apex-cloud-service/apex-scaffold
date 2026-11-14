package com.apex.scaffold.infrastructure.persistence.aspect;

import com.apex.scaffold.common.persistence.po.BasePO;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * 将 MyBatis-Plus 标准 {@code updateById} 的乐观锁冲突转换为 Spring 异常。
 *
 * <p>MyBatis-Plus 乐观锁插件负责追加版本条件并递增版本号，但版本不匹配时只返回
 * {@code 0}，不会主动抛出异常。在 Mapper 调用边界完成转换，可避免异常在 MyBatis
 * Executor 内部被包装为 {@code MyBatisSystemException}，并使上层重试机制可靠识别冲突。
 *
 * <h2>职责范围</h2>
 * <p>切面仅处理 MyBatis-Plus {@code BaseMapper} 及其子接口的标准
 * {@code updateById} 方法。只有更新对象继承 {@link BasePO}、携带非空版本号且更新
 * 行数为 {@code 0} 时，才判定为乐观锁冲突。未携带版本号的更新以及自定义 SQL
 * 不会被此切面误判。
 *
 * <h2>设计说明</h2>
 * <p>检测到冲突后抛出 {@link OptimisticLockingFailureException}，供同步重试切面、
 * Reactor 重试策略或全局异常处理器统一识别。异常消息只包含实体类型和主键，
 * 不输出实体完整内容，避免敏感字段泄露。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see BasePO
 * @see OptimisticLockingFailureException
 */
@Aspect
@Component
@lombok.NoArgsConstructor
public class OptimisticLockUpdateAspect {

    /**
     * 检查标准 {@code updateById} 的返回结果并识别乐观锁冲突。
     *
     * <p>通知在 Mapper 方法正常返回后执行。以下条件同时满足时抛出异常：
     * <ol>
     *   <li>数据库受影响行数为 {@code 0}</li>
     *   <li>更新参数是 {@link BasePO} 或其子类</li>
     *   <li>更新参数携带非空 {@code BasePO.getVersion()} 值</li>
     * </ol>
     *
     * <p>非零更新结果表示更新成功；版本为空则表示本次调用没有启用
     * MyBatis-Plus 乐观锁，此时保持原始返回语义。
     *
     * @param entity       传递给 Mapper 的持久化对象
     * @param affectedRows Mapper 返回的数据库受影响行数
     * @throws OptimisticLockingFailureException 当带版本的按 ID 更新未修改任何记录时抛出
     */
    @AfterReturning(
            pointcut = "execution(int com.baomidou.mybatisplus.core.mapper.BaseMapper+.updateById(..))"
                    + " && args(entity)",
            returning = "affectedRows")
    public void detectConflict(Object entity, int affectedRows) {
        if (affectedRows == 0 && entity instanceof BasePO po && po.getVersion() != null) {
            throw new OptimisticLockingFailureException(
                    "乐观锁更新冲突, entity=" + entity.getClass().getSimpleName() + ", id=" + po.getId());
        }
    }
}
