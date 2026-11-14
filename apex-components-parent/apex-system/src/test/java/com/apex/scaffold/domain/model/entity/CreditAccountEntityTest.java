package com.apex.scaffold.domain.model.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link CreditAccountEntity} 的单元测试类
 *
 * <p>测试积分账户实体的 getter/setter 方法以及继承自 BaseEntity 的行为
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class CreditAccountEntityTest extends BaseEntityTest<CreditAccountEntity> {

    /** {@inheritDoc} */
    @Override
    protected CreditAccountEntity createEntity() {
        return new CreditAccountEntity();
    }

    @Nested
    @DisplayName("setter 和 getter 方法")
    class SetterGetterTest {

        /**
         * 设置{@code tersShouldUpdateFieldsAndGettersShouldReturnCorrectValues}。
         */
        @Test
        @DisplayName("使用 setter 设置属性后 getter 应正确返回")
        void settersShouldUpdateFieldsAndGettersShouldReturnCorrectValues() {
            CreditAccountEntity entity = new CreditAccountEntity();
            entity.setUserId(100L);
            entity.setBalance(500L);

            assertThat(entity.getUserId()).isEqualTo(100L);
            assertThat(entity.getBalance()).isEqualTo(500L);
        }

        @Test
        @DisplayName("新创建的实体字段应为 null")
        void newEntityShouldHaveNullFields() {
            CreditAccountEntity entity = new CreditAccountEntity();
            assertThat(entity.getUserId()).isNull();
            assertThat(entity.getBalance()).isNull();
        }
    }

    @Nested
    @DisplayName("业务场景")
    class BusinessScenarioTest {
        @Test
        @DisplayName("新用户积分账户余额为 0")
        void newUserAccountShouldHaveZeroBalance() {
            CreditAccountEntity entity = new CreditAccountEntity();
            entity.setUserId(1L);
            entity.setBalance(0L);

            assertThat(entity.getUserId()).isEqualTo(1L);
            assertThat(entity.getBalance()).isEqualTo(0L);
        }

        @Test
        @DisplayName("积分充值后余额增加")
        void balanceShouldIncreaseAfterRecharge() {
            CreditAccountEntity entity = new CreditAccountEntity();
            entity.setUserId(1L);
            entity.setBalance(100L);

            entity.setBalance(entity.getBalance() + 50L);
            assertThat(entity.getBalance()).isEqualTo(150L);
        }

        @Test
        @DisplayName("积分消费后余额减少")
        void balanceShouldDecreaseAfterSpending() {
            CreditAccountEntity entity = new CreditAccountEntity();
            entity.setUserId(1L);
            entity.setBalance(200L);

            entity.setBalance(entity.getBalance() - 30L);
            assertThat(entity.getBalance()).isEqualTo(170L);
        }
    }
}
