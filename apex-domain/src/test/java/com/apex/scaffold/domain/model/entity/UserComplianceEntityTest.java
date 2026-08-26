package com.apex.scaffold.domain.model.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link UserComplianceEntity} 的单元测试类
 *
 * <p>测试用户合规配置的创建和青少年模式的开启/关闭等领域行为
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class UserComplianceEntityTest {

    @Nested
    @DisplayName("静态工厂方法 create()")
    class CreateTest {

        /**
         * 执行 {@code shouldSetUserIdAndDefaultTeenModeDisabled} 操作。
         */
        @Test
        @DisplayName("创建时应正确设置 userId 且青少年模式默认关闭")
        void shouldSetUserIdAndDefaultTeenModeDisabled() {
            Long userId = 1L;

            UserComplianceEntity compliance = UserComplianceEntity.create(userId);

            assertEquals(userId, compliance.getUserId());
            assertFalse(compliance.isTeenModeEnabled());
            assertFalse(compliance.isDeleted());
            assertNotNull(compliance.getCreateTime());
            assertNotNull(compliance.getUpdateTime());
        }
    }

    @Nested
    @DisplayName("青少年模式")
    class TeenModeTest {

        /**
         * 执行 {@code defaultShouldNotBeTeenMode} 操作。
         */
        @Test
        @DisplayName("默认不应开启青少年模式")
        void defaultShouldNotBeTeenMode() {
            UserComplianceEntity compliance = UserComplianceEntity.create(1L);
            assertFalse(compliance.isTeenModeEnabled());
        }

        /**
         * 执行 {@code enableTeenModeShouldEnableAndRecordTime} 操作。
         */
        @Test
        @DisplayName("enableTeenMode() 应开启青少年模式并记录时间")
        void enableTeenModeShouldEnableAndRecordTime() {
            UserComplianceEntity compliance = UserComplianceEntity.create(1L);
            compliance.enableTeenMode();
            assertTrue(compliance.isTeenModeEnabled());
            assertNotNull(compliance.getTeenModeSetAt());
        }

        /**
         * 执行 {@code disableTeenModeShouldDisableAndRecordTime} 操作。
         */
        @Test
        @DisplayName("disableTeenMode() 应关闭青少年模式并记录时间")
        void disableTeenModeShouldDisableAndRecordTime() {
            UserComplianceEntity compliance = UserComplianceEntity.create(1L);
            compliance.enableTeenMode();
            assertTrue(compliance.isTeenModeEnabled());

            compliance.disableTeenMode();
            assertFalse(compliance.isTeenModeEnabled());
            assertNotNull(compliance.getTeenModeSetAt());
        }

        /**
         * 执行 {@code repeatedEnableShouldStayEnabled} 操作。
         */
        @Test
        @DisplayName("重复开启应保持开启状态")
        void repeatedEnableShouldStayEnabled() {
            UserComplianceEntity compliance = UserComplianceEntity.create(1L);
            compliance.enableTeenMode();
            compliance.enableTeenMode();
            assertTrue(compliance.isTeenModeEnabled());
        }

        /**
         * 执行 {@code nullTeenModeShouldReturnFalse} 操作。
         */
        @Test
        @DisplayName("teenMode 为 null 时 isTeenModeEnabled 应返回 false")
        void nullTeenModeShouldReturnFalse() {
            UserComplianceEntity compliance = UserComplianceEntity.create(1L);
            compliance.setTeenMode(null);
            assertFalse(compliance.isTeenModeEnabled());
        }
    }

    @Nested
    @DisplayName("TeenModeStatus 枚举")
    class TeenModeStatusTest {

        /**
         * 执行 {@code disabledCodeShouldBeZero} 操作。
         */
        @Test
        @DisplayName("DISABLED 的 code 应为 0")
        void disabledCodeShouldBeZero() {
            assertEquals(0, UserComplianceEntity.TeenModeStatus.DISABLED.getCode());
        }

        /**
         * 执行 {@code enabledCodeShouldBeOne} 操作。
         */
        @Test
        @DisplayName("ENABLED 的 code 应为 1")
        void enabledCodeShouldBeOne() {
            assertEquals(1, UserComplianceEntity.TeenModeStatus.ENABLED.getCode());
        }
    }

    @Nested
    @DisplayName("影子封禁 ShadowBan")
    class ShadowBanTest {

        /**
         * 创建{@code shouldSetShadowBanStageToNone}。
         */
        @Test
        @DisplayName("创建时影子封禁阶段应为 NONE")
        void createShouldSetShadowBanStageToNone() {
            UserComplianceEntity compliance = UserComplianceEntity.create(1L);
            assertEquals("NONE", compliance.getShadowBanStage());
            assertFalse(compliance.isShadowBanned());
        }

        /**
         * 判断是否{@code shadowBannedShouldReturnCorrectStatus}。
         */
        @Test
        @DisplayName("isShadowBanned() 应正确判断封禁状态")
        void isShadowBannedShouldReturnCorrectStatus() {
            UserComplianceEntity compliance = UserComplianceEntity.create(1L);

            assertFalse(compliance.isShadowBanned());

            compliance.setShadowBanStage("STAGE_1");
            assertTrue(compliance.isShadowBanned());

            compliance.setShadowBanStage("STAGE_2");
            assertTrue(compliance.isShadowBanned());

            compliance.setShadowBanStage("NONE");
            assertFalse(compliance.isShadowBanned());

            compliance.setShadowBanStage(null);
            assertFalse(compliance.isShadowBanned());
        }

        /**
         * 执行 {@code escalateShadowBanShouldUpgradeStage} 操作。
         */
        @Test
        @DisplayName("escalateShadowBan() 应逐级升级封禁阶段")
        void escalateShadowBanShouldUpgradeStage() {
            UserComplianceEntity compliance = UserComplianceEntity.create(1L);

            compliance.escalateShadowBan();
            assertEquals("STAGE_1", compliance.getShadowBanStage());
            assertNotNull(compliance.getShadowBanAt());

            compliance.escalateShadowBan();
            assertEquals("STAGE_2", compliance.getShadowBanStage());

            compliance.escalateShadowBan();
            assertEquals("STAGE_3", compliance.getShadowBanStage());

            compliance.escalateShadowBan();
            assertEquals("STAGE_3", compliance.getShadowBanStage());
        }

        /**
         * 执行 {@code liftShadowBanShouldResetStage} 操作。
         */
        @Test
        @DisplayName("liftShadowBan() 应解除封禁并清空封禁时间")
        void liftShadowBanShouldResetStage() {
            UserComplianceEntity compliance = UserComplianceEntity.create(1L);
            compliance.escalateShadowBan();
            compliance.escalateShadowBan();
            assertEquals("STAGE_2", compliance.getShadowBanStage());
            assertNotNull(compliance.getShadowBanAt());

            compliance.liftShadowBan();
            assertEquals("NONE", compliance.getShadowBanStage());
            assertNull(compliance.getShadowBanAt());
            assertFalse(compliance.isShadowBanned());
        }
    }

    @Nested
    @DisplayName("ShadowBanStage 枚举")
    class ShadowBanStageTest {

        /**
         * 执行 {@code stageCodesShouldBeCorrect} 操作。
         */
        @Test
        @DisplayName("各阶段的 code 应正确")
        void stageCodesShouldBeCorrect() {
            assertEquals("NONE", UserComplianceEntity.ShadowBanStage.NONE.getCode());
            assertEquals("STAGE_1", UserComplianceEntity.ShadowBanStage.STAGE_1.getCode());
            assertEquals("STAGE_2", UserComplianceEntity.ShadowBanStage.STAGE_2.getCode());
            assertEquals("STAGE_3", UserComplianceEntity.ShadowBanStage.STAGE_3.getCode());
        }

        /**
         * 执行 {@code fromCodeShouldParseCorrectly} 操作。
         */
        @Test
        @DisplayName("fromCode() 应正确解析阶段编码")
        void fromCodeShouldParseCorrectly() {
            assertEquals(
                    UserComplianceEntity.ShadowBanStage.NONE, UserComplianceEntity.ShadowBanStage.fromCode("NONE"));
            assertEquals(
                    UserComplianceEntity.ShadowBanStage.STAGE_1,
                    UserComplianceEntity.ShadowBanStage.fromCode("STAGE_1"));
            assertEquals(
                    UserComplianceEntity.ShadowBanStage.STAGE_2,
                    UserComplianceEntity.ShadowBanStage.fromCode("STAGE_2"));
            assertEquals(
                    UserComplianceEntity.ShadowBanStage.STAGE_3,
                    UserComplianceEntity.ShadowBanStage.fromCode("STAGE_3"));
        }

        /**
         * 执行 {@code fromCodeWithNullShouldReturnNone} 操作。
         */
        @Test
        @DisplayName("fromCode() 传入 null 应返回 NONE")
        void fromCodeWithNullShouldReturnNone() {
            assertEquals(UserComplianceEntity.ShadowBanStage.NONE, UserComplianceEntity.ShadowBanStage.fromCode(null));
        }

        /**
         * 执行 {@code fromCodeWithInvalidCodeShouldReturnNone} 操作。
         */
        @Test
        @DisplayName("fromCode() 传入无效编码应返回 NONE")
        void fromCodeWithInvalidCodeShouldReturnNone() {
            assertEquals(
                    UserComplianceEntity.ShadowBanStage.NONE, UserComplianceEntity.ShadowBanStage.fromCode("INVALID"));
        }

        /**
         * 执行 {@code nextShouldReturnNextStage} 操作。
         */
        @Test
        @DisplayName("next() 应返回下一阶段")
        void nextShouldReturnNextStage() {
            assertEquals(UserComplianceEntity.ShadowBanStage.STAGE_1, UserComplianceEntity.ShadowBanStage.NONE.next());
            assertEquals(
                    UserComplianceEntity.ShadowBanStage.STAGE_2, UserComplianceEntity.ShadowBanStage.STAGE_1.next());
            assertEquals(
                    UserComplianceEntity.ShadowBanStage.STAGE_3, UserComplianceEntity.ShadowBanStage.STAGE_2.next());
            assertEquals(
                    UserComplianceEntity.ShadowBanStage.STAGE_3, UserComplianceEntity.ShadowBanStage.STAGE_3.next());
        }
    }
}
