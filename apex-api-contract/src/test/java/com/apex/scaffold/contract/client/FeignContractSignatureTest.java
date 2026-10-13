package com.apex.scaffold.contract.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.contract.config.FeignClientConstants;
import com.apex.scaffold.contract.dto.internal.DeptTreeDTO;
import com.apex.scaffold.contract.dto.internal.DictItemDTO;
import com.apex.scaffold.contract.dto.internal.InstanceStatusDTO;
import com.apex.scaffold.contract.dto.internal.TokenValidationDTO;
import com.apex.scaffold.contract.dto.internal.TokenValidationRequest;
import com.apex.scaffold.contract.dto.internal.UserSnapshotDTO;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign 契约签名一致性测试.
 *
 * <p><b>目的</b>：契约模块定位为「提供方实现、消费方引用」的编译期约束。本测试通过反射
 * 校验各 {@code @FeignClient} 接口的签名（路径 / 参数注解 / 返回类型），一旦提供方实现与
 * 消费方引用出现签名漂移，CI 即在此处拦截，保障跨服务调用契约稳定。
 *
 * <p>覆盖迁移方案 §6.2 的 6 个核心内部 API 契约：auth（权限 / Token 校验）、system
 * （用户档案 / 部门树 / 字典）、workflow（实例状态）。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("Feign 契约签名一致性测试")
class FeignContractSignatureTest {

    /** 内部 API 路径前缀（与 {@link FeignClientConstants#INTERNAL_API_PREFIX} 一致） */
    private static final String INTERNAL_PREFIX = FeignClientConstants.INTERNAL_API_PREFIX;

    @Nested
    @DisplayName("AuthFeignClient 契约")
    class AuthContractTest {

        @Test
        @DisplayName("用户权限查询契约签名正确")
        void getUserPermissionsSignature() throws Exception {
            Method method = AuthFeignClient.class.getMethod("getUserPermissions", Long.class, Long.class);
            assertThat(method.getReturnType()).isEqualTo(Result.class);
            GetMapping mapping = method.getAnnotation(GetMapping.class);
            assertThat(mapping).isNotNull();
            assertThat(mapping.value()).containsExactly(INTERNAL_PREFIX + "/users/{accountId}/permissions");
            assertThat(method.getParameters())
                    .allMatch(p ->
                            p.isAnnotationPresent(PathVariable.class) || p.isAnnotationPresent(RequestParam.class));
        }

        @Test
        @DisplayName("Token 校验契约签名正确")
        void validateTokenSignature() throws Exception {
            Method method = AuthFeignClient.class.getMethod("validateToken", TokenValidationRequest.class);
            PostMapping mapping = method.getAnnotation(PostMapping.class);
            assertThat(mapping).isNotNull();
            assertThat(mapping.value()).containsExactly(INTERNAL_PREFIX + "/tokens/validate");
        }
    }

    @Nested
    @DisplayName("SystemFeignClient 契约")
    class SystemContractTest {

        @Test
        @DisplayName("用户档案快照 / 档案 / 部门树 / 字典 契约签名正确")
        void systemSignatures() throws Exception {
            assertThat(getMappingPath(SystemFeignClient.class.getMethod("getUserSnapshot", Long.class)))
                    .isEqualTo(INTERNAL_PREFIX + "/users/{accountId}/snapshot");
            assertThat(getMappingPath(SystemFeignClient.class.getMethod("getUserProfile", Long.class)))
                    .isEqualTo(INTERNAL_PREFIX + "/users/{accountId}");
            assertThat(getMappingPath(SystemFeignClient.class.getMethod("getDeptTree")))
                    .isEqualTo(INTERNAL_PREFIX + "/depts/tree");
            assertThat(getMappingPath(SystemFeignClient.class.getMethod("getDictItems", String.class)))
                    .isEqualTo(INTERNAL_PREFIX + "/dict/{type}");
        }

        @Test
        @DisplayName("返回类型与契约 DTO 绑定")
        void dtoBindings() throws Exception {
            assertThat(isResultOf(SystemFeignClient.class.getMethod("getDeptTree"), List.class))
                    .isTrue();
            assertThat(isResultOf(SystemFeignClient.class.getMethod("getDictItems", String.class), List.class))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("WorkflowFeignClient 契约")
    class WorkflowContractTest {

        @Test
        @DisplayName("实例状态查询契约签名正确")
        void getInstanceStatusSignature() throws Exception {
            assertThat(getMappingPath(WorkflowFeignClient.class.getMethod("getInstanceStatus", String.class)))
                    .isEqualTo(INTERNAL_PREFIX + "/instances/{id}/status");
        }
    }

    @Nested
    @DisplayName("契约 DTO 可序列化性")
    class DtoContractTest {

        @Test
        @DisplayName("内部 API DTO 均实现 Serializable 以支持 Feign 传输")
        void allDtoSerializable() {
            assertThat(UserSnapshotDTO.class).isAssignableTo(Serializable.class);
            assertThat(TokenValidationDTO.class).isAssignableTo(Serializable.class);
            assertThat(TokenValidationRequest.class).isAssignableTo(Serializable.class);
            assertThat(InstanceStatusDTO.class).isAssignableTo(Serializable.class);
            assertThat(DeptTreeDTO.class).isAssignableTo(Serializable.class);
            assertThat(DictItemDTO.class).isAssignableTo(Serializable.class);
        }
    }

    /** 读取方法 {@link GetMapping} 声明的首个路径 */
    private static String getMappingPath(Method method) {
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertThat(mapping).as("方法 %s 应标注 @GetMapping", method.getName()).isNotNull();
        return mapping.value()[0];
    }

    /** 判断某方法返回值是否为 {@code Result<List<T>>} 形态 */
    private static boolean isResultOf(Method method, Class<?> containerType) {
        var genericReturn = method.getGenericReturnType();
        if (!(genericReturn instanceof ParameterizedType parameterizedType)) {
            return false;
        }
        if (!parameterizedType.getRawType().equals(Result.class)) {
            return false;
        }
        var actualArgs = parameterizedType.getActualTypeArguments();
        if (actualArgs.length != 1 || !(actualArgs[0] instanceof ParameterizedType nested)) {
            return false;
        }
        return nested.getRawType().equals(containerType);
    }
}
