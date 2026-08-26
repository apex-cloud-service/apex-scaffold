package com.apex.scaffold.domain.model.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link CreditTxEntity} 的单元测试类
 *
 * <p>测试积分流水实体的 getter/setter 方法以及继承自 BaseEntity 的行为
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class CreditTxEntityTest extends BaseEntityTest<CreditTxEntity> {

    /** {@inheritDoc} */
    @Override
    protected CreditTxEntity createEntity() {
        return new CreditTxEntity();
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
            CreditTxEntity entity = new CreditTxEntity();
            entity.setUserId(100L);
            entity.setDirection("IN");
            entity.setAmount(50L);
            entity.setReason("签到奖励");
            entity.setRefType("SIGN_IN");
            entity.setRefId(200L);

            assertThat(entity.getUserId()).isEqualTo(100L);
            assertThat(entity.getDirection()).isEqualTo("IN");
            assertThat(entity.getAmount()).isEqualTo(50L);
            assertThat(entity.getReason()).isEqualTo("签到奖励");
            assertThat(entity.getRefType()).isEqualTo("SIGN_IN");
            assertThat(entity.getRefId()).isEqualTo(200L);
        }

        /**
         * 执行 {@code newEntityShouldHaveNullFields} 操作。
         */
        @Test
        @DisplayName("新创建的实体字段应为 null")
        void newEntityShouldHaveNullFields() {
            CreditTxEntity entity = new CreditTxEntity();
            assertThat(entity.getUserId()).isNull();
            assertThat(entity.getDirection()).isNull();
            assertThat(entity.getAmount()).isNull();
            assertThat(entity.getReason()).isNull();
            assertThat(entity.getRefType()).isNull();
            assertThat(entity.getRefId()).isNull();
        }
    }

    @Nested
    @DisplayName("业务场景")
    class BusinessScenarioTest {

        /**
         * 执行 {@code shouldSupportIncomeTransaction} 操作。
         */
        @Test
        @DisplayName("积分收入流水")
        void shouldSupportIncomeTransaction() {
            CreditTxEntity entity = new CreditTxEntity();
            entity.setUserId(1L);
            entity.setDirection("IN");
            entity.setAmount(100L);
            entity.setReason("每日签到");

            assertThat(entity.getDirection()).isEqualTo("IN");
            assertThat(entity.getAmount()).isEqualTo(100L);
        }

        /**
         * 执行 {@code shouldSupportOutcomeTransaction} 操作。
         */
        @Test
        @DisplayName("积分支出流水")
        void shouldSupportOutcomeTransaction() {
            CreditTxEntity entity = new CreditTxEntity();
            entity.setUserId(1L);
            entity.setDirection("OUT");
            entity.setAmount(30L);
            entity.setReason("照片解锁");

            assertThat(entity.getDirection()).isEqualTo("OUT");
            assertThat(entity.getAmount()).isEqualTo(30L);
        }

        /**
         * 执行 {@code shouldSupportReferenceType} 操作。
         */
        @Test
        @DisplayName("关联业务类型")
        void shouldSupportReferenceType() {
            CreditTxEntity entity = new CreditTxEntity();
            entity.setUserId(1L);
            entity.setDirection("IN");
            entity.setAmount(200L);
            entity.setRefType("BATTLE_REWARD");
            entity.setRefId(999L);

            assertThat(entity.getRefType()).isEqualTo("BATTLE_REWARD");
            assertThat(entity.getRefId()).isEqualTo(999L);
        }
    }
}
