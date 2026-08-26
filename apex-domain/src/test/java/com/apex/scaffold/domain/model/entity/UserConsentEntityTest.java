package com.apex.scaffold.domain.model.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link UserConsentEntity} 的单元测试类
 *
 * <p>测试用户同意记录实体的 getter/setter 方法
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class UserConsentEntityTest extends BaseEntityTest<UserConsentEntity> {

    /** {@inheritDoc} */
    @Override
    protected UserConsentEntity createEntity() {
        return new UserConsentEntity();
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
            UserConsentEntity consent = new UserConsentEntity();
            consent.setUserId(1L);
            consent.setPolicyType("PRIVACY");
            consent.setVersion("v1.0");
            consent.setAgreed(1);
            LocalDateTime agreedAt = LocalDateTime.now();
            consent.setAgreedAt(agreedAt);
            consent.setChannel("APP");
            consent.setDeviceId("device-123");

            assertThat(consent.getUserId()).isEqualTo(1L);
            assertThat(consent.getPolicyType()).isEqualTo("PRIVACY");
            assertThat(consent.getVersion()).isEqualTo("v1.0");
            assertThat(consent.getAgreed()).isEqualTo(1);
            assertThat(consent.getAgreedAt()).isEqualTo(agreedAt);
            assertThat(consent.getChannel()).isEqualTo("APP");
            assertThat(consent.getDeviceId()).isEqualTo("device-123");
        }

        /**
         * 执行 {@code newEntityShouldHaveNullFields} 操作。
         */
        @Test
        @DisplayName("新创建的实体字段应为 null")
        void newEntityShouldHaveNullFields() {
            UserConsentEntity consent = new UserConsentEntity();
            assertThat(consent.getUserId()).isNull();
            assertThat(consent.getPolicyType()).isNull();
            assertThat(consent.getVersion()).isNull();
            assertThat(consent.getAgreed()).isNull();
            assertThat(consent.getAgreedAt()).isNull();
            assertThat(consent.getChannel()).isNull();
            assertThat(consent.getDeviceId()).isNull();
        }
    }

    @Nested
    @DisplayName("业务场景")
    class BusinessScenarioTest {

        /**
         * 执行 {@code shouldRecordPrivacyConsent} 操作。
         */
        @Test
        @DisplayName("用户同意隐私协议记录")
        void shouldRecordPrivacyConsent() {
            UserConsentEntity consent = new UserConsentEntity();
            consent.setUserId(100L);
            consent.setPolicyType("PRIVACY");
            consent.setVersion("2.0");
            consent.setAgreed(1);
            consent.setAgreedAt(LocalDateTime.now());
            consent.setChannel("MINI_PROGRAM");
            consent.setDeviceId("wechat-device-1");

            assertThat(consent.getAgreed()).isEqualTo(1);
            assertThat(consent.getPolicyType()).isEqualTo("PRIVACY");
            assertThat(consent.getVersion()).isEqualTo("2.0");
        }

        /**
         * 执行 {@code shouldRecordConsentDenial} 操作。
         */
        @Test
        @DisplayName("用户拒绝协议记录")
        void shouldRecordConsentDenial() {
            UserConsentEntity consent = new UserConsentEntity();
            consent.setUserId(200L);
            consent.setPolicyType("TERMS");
            consent.setVersion("v3.1");
            consent.setAgreed(0);

            assertThat(consent.getAgreed()).isEqualTo(0);
        }

        /**
         * 执行 {@code shouldSupportTermsPolicyType} 操作。
         */
        @Test
        @DisplayName("支持服务协议类型")
        void shouldSupportTermsPolicyType() {
            UserConsentEntity consent = new UserConsentEntity();
            consent.setPolicyType("TERMS");
            assertThat(consent.getPolicyType()).isEqualTo("TERMS");
        }

        /**
         * 执行 {@code shouldSupportMultipleChannels} 操作。
         */
        @Test
        @DisplayName("支持多渠道记录")
        void shouldSupportMultipleChannels() {
            UserConsentEntity consent = new UserConsentEntity();
            consent.setChannel("H5");
            assertThat(consent.getChannel()).isEqualTo("H5");
        }
    }
}
