package com.apex.scaffold.contract.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Feign 降级工厂测试.
 *
 * <p>校验 auth / system / workflow 三个服务在不可用（超时 / 熔断 / 宕机）时，
 * {@link org.springframework.cloud.openfeign.FallbackFactory} 均返回
 * {@link ResultCode#SERVICE_UNAVAILABLE} 的失败语义，遵循「拒绝优先」的安全默认：
 * 权限、Token 校验等安全敏感调用绝不能在降级时被放行。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("Feign 降级工厂测试")
class FeignClientFallbackFactoryTest {

    private static final RuntimeException CAUSE = new RuntimeException("connection refused");

    @Nested
    @DisplayName("AuthFeignClientFallbackFactory")
    class AuthFallbackTest {

        private final AuthFeignClient fallback = new AuthFeignClientFallbackFactory().create(CAUSE);

        @Test
        @DisplayName("权限查询降级返回服务不可用（拒绝优先）")
        void permissionsDegraded() {
            Result<?> result = fallback.getUserPermissions(10086L, 1L);
            assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_UNAVAILABLE.getCode());
            assertThat(result.getData()).isNull();
        }

        @Test
        @DisplayName("Token 校验降级返回服务不可用（拒绝优先）")
        void validateTokenDegraded() {
            Result<?> result = fallback.validateToken(null);
            assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_UNAVAILABLE.getCode());
            assertThat(result.getData()).isNull();
        }
    }

    @Nested
    @DisplayName("SystemFeignClientFallbackFactory")
    class SystemFallbackTest {

        private final SystemFeignClient fallback = new SystemFeignClientFallbackFactory().create(CAUSE);

        @Test
        @DisplayName("用户快照 / 档案 / 部门树 / 字典 降级均返回服务不可用")
        void systemDegraded() {
            assertThat(fallback.getUserSnapshot(10086L).getCode()).isEqualTo(ResultCode.SERVICE_UNAVAILABLE.getCode());
            assertThat(fallback.getUserProfile(10086L).getCode()).isEqualTo(ResultCode.SERVICE_UNAVAILABLE.getCode());
            assertThat(fallback.getDeptTree().getCode()).isEqualTo(ResultCode.SERVICE_UNAVAILABLE.getCode());
            assertThat(fallback.getDictItems("sys_user_status").getCode())
                    .isEqualTo(ResultCode.SERVICE_UNAVAILABLE.getCode());
        }
    }

    @Nested
    @DisplayName("WorkflowFeignClientFallbackFactory")
    class WorkflowFallbackTest {

        private final WorkflowFeignClient fallback = new WorkflowFeignClientFallbackFactory().create(CAUSE);

        @Test
        @DisplayName("实例状态查询降级返回服务不可用")
        void instanceStatusDegraded() {
            Result<?> result = fallback.getInstanceStatus("proc-inst-001");
            assertThat(result.getCode()).isEqualTo(ResultCode.SERVICE_UNAVAILABLE.getCode());
            assertThat(result.getData()).isNull();
        }
    }

    @Test
    @DisplayName("降级结果携带服务不可用错误码语义")
    void fallbackCarriesServiceUnavailableSemantic() {
        Result<?> result = new AuthFeignClientFallbackFactory().create(CAUSE).getUserPermissions(1L, 1L);
        assertThat(result.getCode()).isEqualTo(3007);
        assertThat(result.getMsg()).isEqualTo(ResultCode.SERVICE_UNAVAILABLE.getMsg());
    }
}
