package com.apex.scaffold.domain.model.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link OperationLogEntity} 的单元测试类
 *
 * <p>测试操作日志实体的 getter/setter 方法以及继承自 BaseEntity 的行为
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class OperationLogEntityTest extends BaseEntityTest<OperationLogEntity> {

    /** {@inheritDoc} */
    @Override
    protected OperationLogEntity createEntity() {
        return new OperationLogEntity();
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
            OperationLogEntity entity = new OperationLogEntity();
            entity.setModule("用户管理");
            entity.setOperationType("CREATE");
            entity.setDescription("创建新用户");
            entity.setRequestMethod("POST");
            entity.setRequestUri("/api/users");
            entity.setRequestParams("{\"name\":\"test\"}");
            entity.setResponseResult("{\"id\":1}");
            entity.setStatus(0);
            entity.setErrorMsg(null);
            entity.setExecutionTime(150L);
            entity.setIp("192.168.1.1");
            entity.setLocation("北京");
            entity.setUserId(100L);
            entity.setUserName("admin");
            LocalDateTime operationTime = LocalDateTime.now();
            entity.setOperationTime(operationTime);
            entity.setDeviceType("PC");
            entity.setBrowser("Chrome");
            entity.setOs("Windows");
            entity.setTraceId("trace-001");

            assertThat(entity.getModule()).isEqualTo("用户管理");
            assertThat(entity.getOperationType()).isEqualTo("CREATE");
            assertThat(entity.getDescription()).isEqualTo("创建新用户");
            assertThat(entity.getRequestMethod()).isEqualTo("POST");
            assertThat(entity.getRequestUri()).isEqualTo("/api/users");
            assertThat(entity.getRequestParams()).isEqualTo("{\"name\":\"test\"}");
            assertThat(entity.getResponseResult()).isEqualTo("{\"id\":1}");
            assertThat(entity.getStatus()).isEqualTo(0);
            assertThat(entity.getErrorMsg()).isNull();
            assertThat(entity.getExecutionTime()).isEqualTo(150L);
            assertThat(entity.getIp()).isEqualTo("192.168.1.1");
            assertThat(entity.getLocation()).isEqualTo("北京");
            assertThat(entity.getUserId()).isEqualTo(100L);
            assertThat(entity.getUserName()).isEqualTo("admin");
            assertThat(entity.getOperationTime()).isEqualTo(operationTime);
            assertThat(entity.getDeviceType()).isEqualTo("PC");
            assertThat(entity.getBrowser()).isEqualTo("Chrome");
            assertThat(entity.getOs()).isEqualTo("Windows");
            assertThat(entity.getTraceId()).isEqualTo("trace-001");
        }

        /**
         * 执行 {@code newEntityShouldHaveNullFields} 操作。
         */
        @Test
        @DisplayName("新创建的实体字段应为 null")
        void newEntityShouldHaveNullFields() {
            OperationLogEntity entity = new OperationLogEntity();
            assertThat(entity.getModule()).isNull();
            assertThat(entity.getOperationType()).isNull();
            assertThat(entity.getDescription()).isNull();
            assertThat(entity.getRequestMethod()).isNull();
            assertThat(entity.getRequestUri()).isNull();
            assertThat(entity.getRequestParams()).isNull();
            assertThat(entity.getResponseResult()).isNull();
            assertThat(entity.getStatus()).isNull();
            assertThat(entity.getErrorMsg()).isNull();
            assertThat(entity.getExecutionTime()).isNull();
            assertThat(entity.getIp()).isNull();
            assertThat(entity.getLocation()).isNull();
            assertThat(entity.getUserId()).isNull();
            assertThat(entity.getUserName()).isNull();
            assertThat(entity.getOperationTime()).isNull();
            assertThat(entity.getDeviceType()).isNull();
            assertThat(entity.getBrowser()).isNull();
            assertThat(entity.getOs()).isNull();
            assertThat(entity.getTraceId()).isNull();
        }
    }

    @Nested
    @DisplayName("业务场景")
    class BusinessScenarioTest {

        /**
         * 执行 {@code shouldSupportSuccessOperation} 操作。
         */
        @Test
        @DisplayName("成功操作日志")
        void shouldSupportSuccessOperation() {
            OperationLogEntity entity = new OperationLogEntity();
            entity.setModule("照片管理");
            entity.setOperationType("DELETE");
            entity.setDescription("删除照片");
            entity.setStatus(0);
            entity.setExecutionTime(50L);

            assertThat(entity.getStatus()).isEqualTo(0);
            assertThat(entity.getExecutionTime()).isEqualTo(50L);
        }

        /**
         * 执行 {@code shouldSupportFailedOperationWithError} 操作。
         */
        @Test
        @DisplayName("失败操作日志含错误信息")
        void shouldSupportFailedOperationWithError() {
            OperationLogEntity entity = new OperationLogEntity();
            entity.setModule("登录");
            entity.setOperationType("LOGIN");
            entity.setStatus(1);
            entity.setErrorMsg("密码错误");

            assertThat(entity.getStatus()).isEqualTo(1);
            assertThat(entity.getErrorMsg()).isEqualTo("密码错误");
        }

        /**
         * 执行 {@code shouldSupportTraceId} 操作。
         */
        @Test
        @DisplayName("操作日志包含追踪 ID")
        void shouldSupportTraceId() {
            OperationLogEntity entity = new OperationLogEntity();
            entity.setTraceId("trace-abc-123");

            assertThat(entity.getTraceId()).isEqualTo("trace-abc-123");
        }
    }
}
