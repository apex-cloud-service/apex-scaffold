package com.apex.scaffold.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * {@link HttpStatusUtil} 的单元测试类
 *
 * <p>测试业务错误码到HTTP状态码的映射逻辑，覆盖所有分支路径和边界条件。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("HttpStatusUtil 单元测试")
class HttpStatusUtilTest {

    @Nested
    @DisplayName("fromBusinessCode 方法")
    class FromBusinessCodeTests {

        /**
         * 执行 {@code shouldReturnInternalServerErrorWhenCodeIsNull} 操作。
         */
        @Test
        @DisplayName("code 为 null 应返回 500")
        void shouldReturnInternalServerErrorWhenCodeIsNull() {
            assertThat(HttpStatusUtil.fromBusinessCode(null)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        /**
         * 执行 {@code shouldReturnOkWhenCodeIsZero} 操作。
         */
        @Test
        @DisplayName("code 为 0 应返回 200")
        void shouldReturnOkWhenCodeIsZero() {
            assertThat(HttpStatusUtil.fromBusinessCode(0)).isEqualTo(HttpStatus.OK);
        }

        /** 非当前对局属于可恢复的客户端状态冲突。 */
        @Test
        @DisplayName("BATTLE_NOT_CURRENT(4205) 应返回 409")
        void shouldReturnConflictWhenBattleIsNotCurrent() {
            assertThat(HttpStatusUtil.fromBusinessCode(4205)).isEqualTo(HttpStatus.CONFLICT);
        }

        /**
         * 执行 {@code shouldReturnUnauthorizedWhenCodeIs401} 操作。
         */
        @Test
        @DisplayName("code 为 401 应返回 401")
        void shouldReturnUnauthorizedWhenCodeIs401() {
            assertThat(HttpStatusUtil.fromBusinessCode(401)).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        /**
         * 执行 {@code shouldReturnForbiddenWhenCodeIs403} 操作。
         */
        @Test
        @DisplayName("code 为 403 应返回 403")
        void shouldReturnForbiddenWhenCodeIs403() {
            assertThat(HttpStatusUtil.fromBusinessCode(403)).isEqualTo(HttpStatus.FORBIDDEN);
        }

        /**
         * 执行 {@code shouldReturnNotFoundWhenCodeIs404} 操作。
         */
        @Test
        @DisplayName("code 为 404 应返回 404")
        void shouldReturnNotFoundWhenCodeIs404() {
            assertThat(HttpStatusUtil.fromBusinessCode(404)).isEqualTo(HttpStatus.NOT_FOUND);
        }

        /**
         * 执行 {@code shouldReturnBadRequestWhenCodeIs400} 操作。
         */
        @Test
        @DisplayName("code 为 400 应返回 400")
        void shouldReturnBadRequestWhenCodeIs400() {
            assertThat(HttpStatusUtil.fromBusinessCode(400)).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        /**
         * 执行 {@code shouldReturnBadRequestWhenCodeIsOther4xx} 操作。
         */
        @Test
        @DisplayName("code 为其他 4xx（如 422）应返回 400")
        void shouldReturnBadRequestWhenCodeIsOther4xx() {
            assertThat(HttpStatusUtil.fromBusinessCode(422)).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        /**
         * 验证 {@code shouldReturnConflictWhenBattleAlreadySubmitted} 场景下的预期行为。
         *
         */
        @Test
        @DisplayName("对局已结算业务码应返回 409")
        void shouldReturnConflictWhenBattleAlreadySubmitted() {
            assertThat(HttpStatusUtil.fromBusinessCode(ResultCode.BATTLE_ALREADY_SUBMITTED.getCode()))
                    .isEqualTo(HttpStatus.CONFLICT);
        }

        /**
         * 验证 {@code shouldReturnNotFoundWhenNoTargetGenderPhoto} 场景下的预期行为。
         *
         */
        @Test
        @DisplayName("NO_TARGET_GENDER_PHOTO(4204) 应返回 404")
        void shouldReturnNotFoundWhenNoTargetGenderPhoto() {
            assertThat(HttpStatusUtil.fromBusinessCode(ResultCode.NO_TARGET_GENDER_PHOTO.getCode()))
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        /**
         * 执行 {@code shouldReturnBadRequestWhenCodeIs499} 操作。
         */
        @Test
        @DisplayName("code 为 499 应返回 400（4xx上界）")
        void shouldReturnBadRequestWhenCodeIs499() {
            assertThat(HttpStatusUtil.fromBusinessCode(499)).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        /** 文件校验业务码应作为客户端可修正的请求错误返回。 */
        @Test
        @DisplayName("文件校验业务码应返回 400")
        void shouldReturnBadRequestWhenCodeIsFileValidationError() {
            assertThat(HttpStatusUtil.fromBusinessCode(4603)).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        /**
         * 执行 {@code shouldReturnInternalServerErrorWhenCodeIs500} 操作。
         */
        @Test
        @DisplayName("code 为 500 应返回 500")
        void shouldReturnInternalServerErrorWhenCodeIs500() {
            assertThat(HttpStatusUtil.fromBusinessCode(500)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        /**
         * 执行 {@code shouldReturnUnauthorizedWhenCodeIs40101_TokenExpired} 操作。
         *
         * <p>v1.2.0 验证：TOKEN_EXPIRED(40101) 应正确映射为 401 UNAUTHORIZED，
         * 而非原先的 400 BAD_REQUEST，确保 Handler 层使用 TOKEN_EXPIRED
         * 时返回正确的 HTTP 状态码，前端可据此区分 Token 过期场景。</p>
         */
        @Test
        @DisplayName("TOKEN_EXPIRED(40101) 应返回 401 而非 400")
        void shouldReturnUnauthorizedWhenCodeIs40101_TokenExpired() {
            assertThat(HttpStatusUtil.fromBusinessCode(40101)).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        /**
         * 执行 {@code shouldReturnUnauthorizedWhenCodeIs40150} 操作。
         *
         * <p>验证 401xx 段范围映射：40150 应映射为 401 UNAUTHORIZED。</p>
         */
        @Test
        @DisplayName("code 为 40150（认证段中间值）应返回 401")
        void shouldReturnUnauthorizedWhenCodeIs40150() {
            assertThat(HttpStatusUtil.fromBusinessCode(40150)).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        /**
         * 执行 {@code shouldReturnUnauthorizedWhenCodeIs40199} 操作。
         *
         * <p>验证 401xx 段上界：40199 应映射为 401 UNAUTHORIZED。</p>
         */
        @Test
        @DisplayName("code 为 40199（认证段上界）应返回 401")
        void shouldReturnUnauthorizedWhenCodeIs40199() {
            assertThat(HttpStatusUtil.fromBusinessCode(40199)).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        /**
         * 执行 {@code shouldReturnNotFoundWhenCodeIs40401} 操作。
         *
         * <p>验证 404xx 段范围映射：40401 应映射为 404 NOT_FOUND。</p>
         */
        @Test
        @DisplayName("code 为 40401（资源不存在段）应返回 404")
        void shouldReturnNotFoundWhenCodeIs40401() {
            assertThat(HttpStatusUtil.fromBusinessCode(40401)).isEqualTo(HttpStatus.NOT_FOUND);
        }

        /**
         * 执行 {@code shouldReturnInternalServerErrorWhenCodeIs40200} 操作。
         *
         * <p>40200 不在任何已定义的业务错误码段内（介于 401xx 和 404xx 之间），
         * 应回落为 500。</p>
         */
        @Test
        @DisplayName("code 为 40200（段间隙值）应返回 500")
        void shouldReturnInternalServerErrorWhenCodeIs40200() {
            assertThat(HttpStatusUtil.fromBusinessCode(40200)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        /**
         * 执行 {@code shouldReturnBadRequestWhenCodeIs40000} 操作。
         *
         * <p>40000 不在任何已定义的业务错误码段内，应回落为 500。</p>
         */
        @Test
        @DisplayName("code 为 40000（无对应段）应返回 500")
        void shouldReturnBadRequestWhenCodeIs40000() {
            assertThat(HttpStatusUtil.fromBusinessCode(40000)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        /**
         * 执行 {@code shouldReturnInternalServerErrorWhenCodeIs40500} 操作。
         *
         * <p>40500 超出 404xx 段上界，不在任何已定义的业务错误码段内，
         * 应回落为 500。</p>
         */
        @Test
        @DisplayName("code 为 40500（段间隙值）应返回 500")
        void shouldReturnInternalServerErrorWhenCodeIs40500() {
            assertThat(HttpStatusUtil.fromBusinessCode(40500)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        /**
         * 执行 {@code shouldReturnInternalServerErrorWhenCodeIs502} 操作。
         */
        @Test
        @DisplayName("code 为 502 应返回 500")
        void shouldReturnInternalServerErrorWhenCodeIs502() {
            assertThat(HttpStatusUtil.fromBusinessCode(502)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
