package com.apex.scaffold.common.constant;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * SystemConstants 常量类测试
 *
 * <p>验证 SystemConstants 类中所有常量的值以及类定义的规范性
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("SystemConstants 常量测试")
class SystemConstantsTest {
    @Test
    @DisplayName("验证 SystemConstants 是 final 类且构造函数为私有")
    void shouldBeFinalClassWithPrivateConstructor() throws Exception {
        assertTrue(Modifier.isFinal(SystemConstants.class.getModifiers()), "SystemConstants 应为 final 类");

        Constructor<?>[] constructors = SystemConstants.class.getDeclaredConstructors();
        assertEquals(1, constructors.length, "应只有一个构造函数");
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()), "构造函数应为 private");
    }

    @Nested
    @DisplayName("状态常量测试")
    class StatusConstantsTest {
        @Test
        @DisplayName("STATUS_NORMAL 应为 1")
        void shouldHaveCorrectStatusNormal() {
            assertEquals(1, SystemConstants.STATUS_NORMAL);
        }

        @Test
        @DisplayName("STATUS_DISABLED 应为 0")
        void shouldHaveCorrectStatusDisabled() {
            assertEquals(0, SystemConstants.STATUS_DISABLED);
        }
    }

    @Nested
    @DisplayName("删除标记常量测试")
    class DeleteFlagConstantsTest {
        @Test
        @DisplayName("NOT_DELETED 应为 0")
        void shouldHaveCorrectNotDeleted() {
            assertEquals(0, SystemConstants.NOT_DELETED);
        }

        @Test
        @DisplayName("DELETED 应为 1")
        void shouldHaveCorrectDeleted() {
            assertEquals(1, SystemConstants.DELETED);
        }
    }

    @Nested
    @DisplayName("性别常量测试")
    class GenderConstantsTest {
        @Test
        @DisplayName("GENDER_UNKNOWN 应为 0")
        void shouldHaveCorrectGenderUnknown() {
            assertEquals(0, SystemConstants.GENDER_UNKNOWN);
        }

        @Test
        @DisplayName("GENDER_MALE 应为 1")
        void shouldHaveCorrectGenderMale() {
            assertEquals(1, SystemConstants.GENDER_MALE);
        }

        @Test
        @DisplayName("GENDER_FEMALE 应为 2")
        void shouldHaveCorrectGenderFemale() {
            assertEquals(2, SystemConstants.GENDER_FEMALE);
        }
    }

    @Nested
    @DisplayName("操作日志状态常量测试")
    class LogStatusConstantsTest {
        @Test
        @DisplayName("LOG_STATUS_SUCCESS 应为 0")
        void shouldHaveCorrectLogStatusSuccess() {
            assertEquals(0, SystemConstants.LOG_STATUS_SUCCESS);
        }

        @Test
        @DisplayName("LOG_STATUS_FAIL 应为 1")
        void shouldHaveCorrectLogStatusFail() {
            assertEquals(1, SystemConstants.LOG_STATUS_FAIL);
        }
    }

    @Nested
    @DisplayName("分页常量测试")
    class PageConstantsTest {
        @Test
        @DisplayName("DEFAULT_PAGE_NUM 应为 1")
        void shouldHaveCorrectDefaultPageNum() {
            assertEquals(1, SystemConstants.DEFAULT_PAGE_NUM);
        }

        @Test
        @DisplayName("DEFAULT_PAGE_SIZE 应为 10")
        void shouldHaveCorrectDefaultPageSize() {
            assertEquals(10, SystemConstants.DEFAULT_PAGE_SIZE);
        }

        @Test
        @DisplayName("MAX_PAGE_SIZE 应为 100")
        void shouldHaveCorrectMaxPageSize() {
            assertEquals(100, SystemConstants.MAX_PAGE_SIZE);
        }
    }

    @Nested
    @DisplayName("Token 常量测试")
    class TokenConstantsTest {
        @Test
        @DisplayName("TOKEN_PREFIX 应为 'Bearer '")
        void shouldHaveCorrectTokenPrefix() {
            assertEquals("Bearer ", SystemConstants.TOKEN_PREFIX);
        }

        @Test
        @DisplayName("TOKEN_HEADER 应为 'Authorization'")
        void shouldHaveCorrectTokenHeader() {
            assertEquals("Authorization", SystemConstants.TOKEN_HEADER);
        }
    }

    @Nested
    @DisplayName("请求属性常量测试")
    class RequestAttributeConstantsTest {
        @Test
        @DisplayName("USER_ID_ATTR 应为 'userId'")
        void shouldHaveCorrectUserIdAttr() {
            assertEquals("userId", SystemConstants.USER_ID_ATTR);
        }

        @Test
        @DisplayName("USER_NAME_ATTR 应为 'userName'")
        void shouldHaveCorrectUserNameAttr() {
            assertEquals("userName", SystemConstants.USER_NAME_ATTR);
        }

        @Test
        @DisplayName("OPEN_ID_ATTR 应为 'openId'")
        void shouldHaveCorrectOpenIdAttr() {
            assertEquals("openId", SystemConstants.OPEN_ID_ATTR);
        }

        @Test
        @DisplayName("TRACE_ID_HEADER 应为 'X-Trace-Id'")
        void shouldHaveCorrectTraceIdHeader() {
            assertEquals("X-Trace-Id", SystemConstants.TRACE_ID_HEADER);
        }
    }

    @Nested
    @DisplayName("布尔常量测试")
    class BooleanConstantsTest {
        @Test
        @DisplayName("BOOLEAN_TRUE 应为 1")
        void shouldHaveCorrectBooleanTrue() {
            assertEquals(1, SystemConstants.BOOLEAN_TRUE);
        }

        @Test
        @DisplayName("BOOLEAN_FALSE 应为 0")
        void shouldHaveCorrectBooleanFalse() {
            assertEquals(0, SystemConstants.BOOLEAN_FALSE);
        }
    }

    @Nested
    @DisplayName("错误码常量测试")
    class ErrorCodeConstantsTest {
        @Test
        @DisplayName("ERROR_CODE_RATE_LIMIT 应为 2001")
        void shouldHaveCorrectErrorCodeRateLimit() {
            assertEquals(2001, SystemConstants.ERROR_CODE_RATE_LIMIT);
        }

        @Test
        @DisplayName("ERROR_CODE_SERVICE_UNAVAILABLE 应为 2002")
        void shouldHaveCorrectErrorCodeServiceUnavailable() {
            assertEquals(2002, SystemConstants.ERROR_CODE_SERVICE_UNAVAILABLE);
        }

        @Test
        @DisplayName("ERROR_CODE_DEGRADE 应为 2003")
        void shouldHaveCorrectErrorCodeDegrade() {
            assertEquals(2003, SystemConstants.ERROR_CODE_DEGRADE);
        }
    }

    @Nested
    @DisplayName("业务常量测试")
    class BusinessConstantsTest {
        @Test
        @DisplayName("SAME_FREQUENCY_UNLOCK_MIN_SIGNALS 应为 10")
        void shouldHaveCorrectSameFrequencyUnlockMinSignals() {
            assertEquals(10, SystemConstants.SAME_FREQUENCY_UNLOCK_MIN_SIGNALS);
        }
    }

    @Nested
    @DisplayName("全面一致性测试")
    class ConsistencyTest {
        @Test
        @DisplayName("STATUS_NORMAL 和 STATUS_DISABLED 应互斥且非相等")
        void statusNormalAndDisabledShouldBeDifferent() {
            assertNotEquals(SystemConstants.STATUS_NORMAL, SystemConstants.STATUS_DISABLED);
        }

        @Test
        @DisplayName("NOT_DELETED 和 DELETED 应互斥且非相等")
        void notDeletedAndDeletedShouldBeDifferent() {
            assertNotEquals(SystemConstants.NOT_DELETED, SystemConstants.DELETED);
        }

        @Test
        @DisplayName("三个性别常量应互不相同")
        void genderConstantsShouldAllBeDifferent() {
            assertNotEquals(SystemConstants.GENDER_UNKNOWN, SystemConstants.GENDER_MALE);
            assertNotEquals(SystemConstants.GENDER_MALE, SystemConstants.GENDER_FEMALE);
            assertNotEquals(SystemConstants.GENDER_FEMALE, SystemConstants.GENDER_UNKNOWN);
        }

        @Test
        @DisplayName("BOOLEAN_TRUE 和 BOOLEAN_FALSE 应互不相同")
        void booleanConstantsShouldBeDifferent() {
            assertNotEquals(SystemConstants.BOOLEAN_TRUE, SystemConstants.BOOLEAN_FALSE);
        }
    }
}
