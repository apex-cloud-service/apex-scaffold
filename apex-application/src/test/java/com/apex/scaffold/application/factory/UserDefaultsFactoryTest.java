package com.apex.scaffold.application.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.domain.model.entity.UserComplianceEntity;
import com.apex.scaffold.domain.model.entity.UserProfileEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link UserDefaultsFactory} 的单元测试类
 *
 * <p>测试用户默认实体工厂的核心逻辑：创建默认合规配置和创建默认用户画像。
 * </p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("UserDefaultsFactory 单元测试")
class UserDefaultsFactoryTest {

    private final UserDefaultsFactory factory = new UserDefaultsFactory();

    @Nested
    @DisplayName("createDefaultCompliance 方法")
    class CreateDefaultComplianceTests {
        @Test
        @DisplayName("应创建包含正确用户ID的默认合规配置")
        void shouldCreateDefaultComplianceWithCorrectUserId() {
            UserComplianceEntity result = factory.createDefaultCompliance(100L);

            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("应创建青少年模式关闭的默认合规配置")
        void shouldCreateDefaultComplianceWithTeenModeDisabled() {
            UserComplianceEntity result = factory.createDefaultCompliance(200L);

            assertThat(result.getUserId()).isEqualTo(200L);
            assertThat(result.getTeenMode()).isEqualTo(UserComplianceEntity.TeenModeStatus.DISABLED.getCode());
        }

        @Test
        @DisplayName("应设置为非删除状态")
        void shouldCreateDefaultComplianceWithNotDeletedStatus() {
            UserComplianceEntity result = factory.createDefaultCompliance(300L);

            assertThat(result.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("应自动设置创建时间和更新时间")
        void shouldAutoSetTimestamps() {
            UserComplianceEntity result = factory.createDefaultCompliance(400L);

            assertThat(result.getCreateTime()).isNotNull();
            assertThat(result.getUpdateTime()).isNotNull();
            assertThat(result.getCreateTime()).isEqualTo(result.getUpdateTime());
        }
    }

    @Nested
    @DisplayName("createDefaultProfile 方法")
    class CreateDefaultProfileTests {
        @Test
        @DisplayName("应创建包含正确用户ID的默认画像")
        void shouldCreateDefaultProfileWithCorrectUserId() {
            UserProfileEntity result = factory.createDefaultProfile(100L);

            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("应创建普通用户类型的默认画像")
        void shouldCreateDefaultProfileAsNormalUser() {
            UserProfileEntity result = factory.createDefaultProfile(200L);

            assertThat(result.getSocialUser()).isEqualTo(UserProfileEntity.SocialUserType.NORMAL.getCode());
            assertThat(result.isSocialUser()).isFalse();
        }

        @Test
        @DisplayName("应设置为非删除状态")
        void shouldCreateDefaultProfileWithNotDeletedStatus() {
            UserProfileEntity result = factory.createDefaultProfile(300L);

            assertThat(result.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("应自动设置创建时间和更新时间")
        void shouldAutoSetTimestamps() {
            UserProfileEntity result = factory.createDefaultProfile(400L);

            assertThat(result.getCreateTime()).isNotNull();
            assertThat(result.getUpdateTime()).isNotNull();
            assertThat(result.getCreateTime()).isEqualTo(result.getUpdateTime());
        }

        @Test
        @DisplayName("传入null用户ID时应抛出异常")
        void shouldThrowExceptionWhenUserIdIsNull() {
            assertThatThrownBy(() -> factory.createDefaultProfile(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("用户ID不能为空");
        }
    }
}
