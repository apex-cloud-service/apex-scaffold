package com.apex.scaffold.common.result;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ResultCode 响应状态码枚举测试
 *
 * <p>验证 ResultCode 枚举的所有值及其 code/message 对
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("ResultCode 响应状态码枚举测试")
class ResultCodeTest {

    @Nested
    @DisplayName("基础状态码测试")
    class BasicCodesTest {
        @Test
        @DisplayName("SUCCESS 的 code=0, msg='成功'")
        void successShouldBeCorrect() {
            assertEquals(0, ResultCode.SUCCESS.getCode());
            assertEquals("成功", ResultCode.SUCCESS.getMsg());
        }

        @Test
        @DisplayName("ERROR 的 code=-1, msg='系统错误'")
        void errorShouldBeCorrect() {
            assertEquals(-1, ResultCode.ERROR.getCode());
            assertEquals("系统错误", ResultCode.ERROR.getMsg());
        }
    }

    @Nested
    @DisplayName("客户端错误码测试")
    class ClientErrorCodesTest {
        @Test
        @DisplayName("PARAM_ERROR 的 code=400, msg='参数错误'")
        void paramErrorShouldBeCorrect() {
            assertEquals(400, ResultCode.PARAM_ERROR.getCode());
            assertEquals("参数错误", ResultCode.PARAM_ERROR.getMsg());
        }

        @Test
        @DisplayName("UNAUTHORIZED 的 code=401, msg='未授权'")
        void unauthorizedShouldBeCorrect() {
            assertEquals(401, ResultCode.UNAUTHORIZED.getCode());
            assertEquals("未授权", ResultCode.UNAUTHORIZED.getMsg());
        }

        @Test
        @DisplayName("FORBIDDEN 的 code=403, msg='禁止访问'")
        void forbiddenShouldBeCorrect() {
            assertEquals(403, ResultCode.FORBIDDEN.getCode());
            assertEquals("禁止访问", ResultCode.FORBIDDEN.getMsg());
        }

        @Test
        @DisplayName("NOT_FOUND 的 code=404, msg='资源不存在'")
        void notFoundShouldBeCorrect() {
            assertEquals(404, ResultCode.NOT_FOUND.getCode());
            assertEquals("资源不存在", ResultCode.NOT_FOUND.getMsg());
        }
    }

    @Nested
    @DisplayName("微信相关错误码测试")
    class WechatErrorCodesTest {
        @Test
        @DisplayName("WX_LOGIN_FAILED 的 code=1001, msg='微信登录失败'")
        void wxLoginFailedShouldBeCorrect() {
            assertEquals(1001, ResultCode.WX_LOGIN_FAILED.getCode());
            assertEquals("微信登录失败", ResultCode.WX_LOGIN_FAILED.getMsg());
        }

        @Test
        @DisplayName("WX_API_ERROR 的 code=1003, msg='微信接口调用失败'")
        void wxApiErrorShouldBeCorrect() {
            assertEquals(1003, ResultCode.WX_API_ERROR.getCode());
            assertEquals("微信接口调用失败", ResultCode.WX_API_ERROR.getMsg());
        }

        @Test
        @DisplayName("WX_AUTH_FAILED 的 code=1004, msg='微信授权失败'")
        void wxAuthFailedShouldBeCorrect() {
            assertEquals(1004, ResultCode.WX_AUTH_FAILED.getCode());
            assertEquals("微信授权失败", ResultCode.WX_AUTH_FAILED.getMsg());
        }

        @Test
        @DisplayName("WX_USER_INFO_ERROR 的 code=1005, msg='微信用户信息获取失败'")
        void wxUserInfoErrorShouldBeCorrect() {
            assertEquals(1005, ResultCode.WX_USER_INFO_ERROR.getCode());
            assertEquals("微信用户信息获取失败", ResultCode.WX_USER_INFO_ERROR.getMsg());
        }

        @Test
        @DisplayName("UNSUPPORTED_LOGIN_TYPE 的 code=1006, msg='不支持的登录类型'")
        void unsupportedLoginTypeShouldBeCorrect() {
            assertEquals(1006, ResultCode.UNSUPPORTED_LOGIN_TYPE.getCode());
            assertEquals("不支持的登录类型", ResultCode.UNSUPPORTED_LOGIN_TYPE.getMsg());
        }

        @Test
        @DisplayName("LOGIN_FAILED 的 code=1007, msg='登录失败'")
        void loginFailedShouldBeCorrect() {
            assertEquals(1007, ResultCode.LOGIN_FAILED.getCode());
            assertEquals("登录失败", ResultCode.LOGIN_FAILED.getMsg());
        }
    }

    @Nested
    @DisplayName("流量治理错误码测试")
    class GovernanceErrorCodesTest {
        @Test
        @DisplayName("RATE_LIMIT 的 code=2001, msg='请求过于频繁'")
        void rateLimitShouldBeCorrect() {
            assertEquals(2001, ResultCode.RATE_LIMIT.getCode());
            assertEquals("请求过于频繁", ResultCode.RATE_LIMIT.getMsg());
        }

        @Test
        @DisplayName("CIRCUIT_BREAKER 的 code=2002, msg='服务熔断'")
        void circuitBreakerShouldBeCorrect() {
            assertEquals(2002, ResultCode.CIRCUIT_BREAKER.getCode());
            assertEquals("服务熔断", ResultCode.CIRCUIT_BREAKER.getMsg());
        }

        @Test
        @DisplayName("DEGRADE 的 code=2003, msg='服务降级'")
        void degradeShouldBeCorrect() {
            assertEquals(2003, ResultCode.DEGRADE.getCode());
            assertEquals("服务降级", ResultCode.DEGRADE.getMsg());
        }
    }

    @Nested
    @DisplayName("基础设施错误码测试")
    class InfrastructureErrorCodesTest {
        @Test
        @DisplayName("CACHE_ERROR 的 code=3001, msg='缓存错误'")
        void cacheErrorShouldBeCorrect() {
            assertEquals(3001, ResultCode.CACHE_ERROR.getCode());
            assertEquals("缓存错误", ResultCode.CACHE_ERROR.getMsg());
        }

        @Test
        @DisplayName("DB_ERROR 的 code=3002, msg='数据库错误'")
        void dbErrorShouldBeCorrect() {
            assertEquals(3002, ResultCode.DB_ERROR.getCode());
            assertEquals("数据库错误", ResultCode.DB_ERROR.getMsg());
        }

        @Test
        @DisplayName("MQ_ERROR 的 code=3003, msg='消息队列错误'")
        void mqErrorShouldBeCorrect() {
            assertEquals(3003, ResultCode.MQ_ERROR.getCode());
            assertEquals("消息队列错误", ResultCode.MQ_ERROR.getMsg());
        }
    }

    @Nested
    @DisplayName("业务错误码测试")
    class BusinessErrorCodesTest {
        @Test
        @DisplayName("USER_NOT_FOUND 的 code=4001, msg='用户不存在'")
        void userNotFoundShouldBeCorrect() {
            assertEquals(4001, ResultCode.USER_NOT_FOUND.getCode());
            assertEquals("用户不存在", ResultCode.USER_NOT_FOUND.getMsg());
        }

        @Test
        @DisplayName("USER_ALREADY_EXISTS 的 code=4002, msg='用户已存在'")
        void userAlreadyExistsShouldBeCorrect() {
            assertEquals(4002, ResultCode.USER_ALREADY_EXISTS.getCode());
            assertEquals("用户已存在", ResultCode.USER_ALREADY_EXISTS.getMsg());
        }

        @Test
        @DisplayName("SAME_FREQUENCY_LOCKED 的 code=4101, msg='同频未解锁'")
        void sameFrequencyLockedShouldBeCorrect() {
            assertEquals(4101, ResultCode.SAME_FREQUENCY_LOCKED.getCode());
            assertEquals("同频未解锁", ResultCode.SAME_FREQUENCY_LOCKED.getMsg());
        }

        @Test
        @DisplayName("PROFILE_INCOMPLETE 的 code=4102, msg='资料未补完'")
        void profileIncompleteShouldBeCorrect() {
            assertEquals(4102, ResultCode.PROFILE_INCOMPLETE.getCode());
            assertEquals("资料未补完", ResultCode.PROFILE_INCOMPLETE.getMsg());
        }

        @Test
        @DisplayName("TEEN_MODE_RESTRICTED 的 code=4103, msg='青少年模式限制'")
        void teenModeRestrictedShouldBeCorrect() {
            assertEquals(4103, ResultCode.TEEN_MODE_RESTRICTED.getCode());
            assertEquals("青少年模式限制", ResultCode.TEEN_MODE_RESTRICTED.getMsg());
        }

        @Test
        @DisplayName("SKIP_LIMIT_REACHED 的 code=4201, msg='跳过次数已达上限'")
        void skipLimitReachedShouldBeCorrect() {
            assertEquals(4201, ResultCode.SKIP_LIMIT_REACHED.getCode());
            assertEquals("跳过次数已达上限", ResultCode.SKIP_LIMIT_REACHED.getMsg());
        }

        @Test
        @DisplayName("BATTLE_ALREADY_SUBMITTED 的 code=4202, msg='对局已结算/不可重复提交'")
        void battleAlreadySubmittedShouldBeCorrect() {
            assertEquals(4202, ResultCode.BATTLE_ALREADY_SUBMITTED.getCode());
            assertEquals("对局已结算/不可重复提交", ResultCode.BATTLE_ALREADY_SUBMITTED.getMsg());
        }

        /** 非当前交付对局应使用可识别的状态冲突业务码。 */
        @Test
        @DisplayName("BATTLE_NOT_CURRENT 的 code=4205")
        void battleNotCurrentShouldBeCorrect() {
            assertEquals(4205, ResultCode.BATTLE_NOT_CURRENT.getCode());
            assertEquals("对局不是当前可操作对局，请重新同步", ResultCode.BATTLE_NOT_CURRENT.getMsg());
        }

        /**
         */
        @Test
        @DisplayName("CONTENT_NOT_VISIBLE 的 code=4301, msg='内容不可见'")
        void contentNotVisibleShouldBeCorrect() {
            assertEquals(4301, ResultCode.CONTENT_NOT_VISIBLE.getCode());
            assertEquals("内容不可见", ResultCode.CONTENT_NOT_VISIBLE.getMsg());
        }

        @Test
        @DisplayName("TAG_AUDIT_REQUIRED 的 code=4401, msg='标签审核中/未通过'")
        void tagAuditRequiredShouldBeCorrect() {
            assertEquals(4401, ResultCode.TAG_AUDIT_REQUIRED.getCode());
            assertEquals("标签审核中/未通过", ResultCode.TAG_AUDIT_REQUIRED.getMsg());
        }

        @Test
        @DisplayName("MEMBERSHIP_REQUIRED 的 code=4501, msg='会员权益不足'")
        void membershipRequiredShouldBeCorrect() {
            assertEquals(4501, ResultCode.MEMBERSHIP_REQUIRED.getCode());
            assertEquals("会员权益不足", ResultCode.MEMBERSHIP_REQUIRED.getMsg());
        }
    }

    @Nested
    @DisplayName("枚举整体性测试")
    class EnumIntegrityTest {
        @Test
        @DisplayName("所有枚举值的 code 均不应为 null")
        void allCodesShouldNotBeNull() {
            for (ResultCode rc : ResultCode.values()) {
                assertNotNull(rc.getCode(), rc.name() + " 的 code 不应为 null");
            }
        }

        @Test
        @DisplayName("所有枚举值的 msg 均不应为 null 或空")
        void allMsgsShouldNotBeNullOrEmpty() {
            for (ResultCode rc : ResultCode.values()) {
                assertNotNull(rc.getMsg(), rc.name() + " 的 msg 不应为 null");
                assertFalse(rc.getMsg().isEmpty(), rc.name() + " 的 msg 不应为空");
            }
        }

        @Test
        @DisplayName("所有枚举值的 code 应各不重复")
        void allCodesShouldBeUnique() {
            long distinctCount = java.util.Arrays.stream(ResultCode.values())
                    .map(ResultCode::getCode)
                    .distinct()
                    .count();
            assertEquals(ResultCode.values().length, distinctCount, "所有 code 应各不重复");
        }

        @Test
        @DisplayName("valueOf 应能通过 name 获取所有枚举值")
        void valueOfShouldWorkForAllValues() {
            for (ResultCode rc : ResultCode.values()) {
                assertEquals(rc, ResultCode.valueOf(rc.name()));
            }
        }

        @Test
        @DisplayName("ordinal 值应从 0 开始有序排列")
        void ordinalsShouldBeSequentialFromZero() {
            ResultCode[] values = ResultCode.values();
            for (int i = 0; i < values.length; i++) {
                assertEquals(i, values[i].ordinal());
            }
        }

        @Test
        @DisplayName("SUCCESS 的 ordinal 应为 0")
        void successShouldBeFirst() {
            assertEquals(0, ResultCode.SUCCESS.ordinal());
        }

        /** 钉住日限额错误码是当前枚举定义的最后一项。 */
        @Test
        @DisplayName("PIN_DAILY_LIMIT_REACHED 应为最后一个枚举值")
        void pinDailyLimitReachedShouldBeLast() {
            assertEquals(ResultCode.values().length - 1, ResultCode.PIN_DAILY_LIMIT_REACHED.ordinal());
        }
    }
}
