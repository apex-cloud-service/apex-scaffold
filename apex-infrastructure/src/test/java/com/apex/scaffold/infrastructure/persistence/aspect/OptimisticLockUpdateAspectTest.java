package com.apex.scaffold.infrastructure.persistence.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.infrastructure.persistence.mapper.UserMapper;
import com.apex.scaffold.infrastructure.persistence.po.UserPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * {@link OptimisticLockUpdateAspect} 单元测试。
 *
 * <p>验证切面对 MyBatis-Plus Mapper 子接口的实际 AOP 匹配，以及版本为空时的
 * 非冲突分支，防止零行更新被错误分类。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("乐观锁更新结果检查")
class OptimisticLockUpdateAspectTest {

    /**
     * 被测的无状态乐观锁更新切面。
     */
    private final OptimisticLockUpdateAspect aspect = new OptimisticLockUpdateAspect();

    /**
     * 验证带版本对象发生零行更新时会抛出标准 Spring 乐观锁异常。
     *
     * <p>测试通过 {@link AspectJProxyFactory} 创建真实 AOP 代理，而不是直接调用
     * 通知方法，从而同时校验 {@code BaseMapper.updateById} 切点表达式能够匹配
     * 项目的 Mapper 子接口。
     */
    @Test
    @DisplayName("带版本的 updateById 更新 0 行时应抛出乐观锁异常")
    void shouldThrowForVersionedUpdateByIdConflict() {
        UserMapper target = mock(UserMapper.class);
        when(target.updateById(any(UserPO.class))).thenReturn(0);
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.addAspect(aspect);
        UserMapper mapper = proxyFactory.getProxy();
        UserPO user = new UserPO();
        user.setId(1L);
        user.setVersion(3);

        assertThatThrownBy(() -> mapper.updateById(user))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessageContaining("UserPO")
                .hasMessageContaining("id=1");
    }

    /**
     * 验证未携带版本号的零行更新不会被误判为乐观锁冲突。
     *
     * <p>版本为空表示 MyBatis-Plus 本次不会追加版本比较条件，零行结果也可能只是
     * 记录不存在，因此切面必须保持正常返回。
     */
    @Test
    @DisplayName("未携带版本时不应把 0 行更新误判为乐观锁冲突")
    void shouldIgnoreUnversionedUpdate() {
        UserPO user = new UserPO();
        user.setId(1L);

        aspect.detectConflict(user, 0);

        assertThat(user.getVersion()).isNull();
    }
}
