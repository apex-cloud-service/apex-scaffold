package com.apex.scaffold.domain.model.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link PolicyVersionEntity} 的单元测试类
 *
 * <p>测试协议版本实体的 getter/setter 方法以及继承自 BaseEntity 的行为
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class PolicyVersionEntityTest extends BaseEntityTest<PolicyVersionEntity> {

    /** {@inheritDoc} */
    @Override
    protected PolicyVersionEntity createEntity() {
        return new PolicyVersionEntity();
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
            PolicyVersionEntity entity = new PolicyVersionEntity();
            entity.setPolicyType("PRIVACY_POLICY");
            entity.setVersion("v2.0");
            entity.setEffectiveAt(LocalDateTime.parse("2025-06-01T00:00:00"));
            entity.setContentUrl("https://example.com/privacy/v2.html");

            assertThat(entity.getPolicyType()).isEqualTo("PRIVACY_POLICY");
            assertThat(entity.getVersion()).isEqualTo("v2.0");
            assertThat(entity.getEffectiveAt()).isEqualTo(LocalDateTime.parse("2025-06-01T00:00:00"));
            assertThat(entity.getContentUrl()).isEqualTo("https://example.com/privacy/v2.html");
        }

        /**
         * 执行 {@code newEntityShouldHaveNullFields} 操作。
         */
        @Test
        @DisplayName("新创建的实体字段应为 null")
        void newEntityShouldHaveNullFields() {
            PolicyVersionEntity entity = new PolicyVersionEntity();
            assertThat(entity.getPolicyType()).isNull();
            assertThat(entity.getVersion()).isNull();
            assertThat(entity.getEffectiveAt()).isNull();
            assertThat(entity.getContentUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("业务场景")
    class BusinessScenarioTest {

        /**
         * 执行 {@code shouldSupportUserAgreementPolicy} 操作。
         */
        @Test
        @DisplayName("用户协议版本")
        void shouldSupportUserAgreementPolicy() {
            PolicyVersionEntity entity = new PolicyVersionEntity();
            entity.setPolicyType("USER_AGREEMENT");
            entity.setVersion("v3.0");
            entity.setEffectiveAt(LocalDateTime.parse("2025-07-01T00:00:00"));

            assertThat(entity.getPolicyType()).isEqualTo("USER_AGREEMENT");
            assertThat(entity.getVersion()).isEqualTo("v3.0");
        }

        /**
         * 执行 {@code shouldSupportPrivacyPolicyType} 操作。
         */
        @Test
        @DisplayName("隐私政策版本")
        void shouldSupportPrivacyPolicyType() {
            PolicyVersionEntity entity = new PolicyVersionEntity();
            entity.setPolicyType("PRIVACY_POLICY");
            entity.setVersion("v1.5");

            assertThat(entity.getPolicyType()).isEqualTo("PRIVACY_POLICY");
            assertThat(entity.getVersion()).isEqualTo("v1.5");
        }
    }
}
