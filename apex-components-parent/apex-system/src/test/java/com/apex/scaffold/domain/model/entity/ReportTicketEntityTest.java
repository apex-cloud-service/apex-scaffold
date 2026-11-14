package com.apex.scaffold.domain.model.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ReportTicketEntity} 的单元测试类
 *
 * <p>测试举报工单实体的 getter/setter 方法以及兼容字段映射
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class ReportTicketEntityTest extends BaseEntityTest<ReportTicketEntity> {

    /** {@inheritDoc} */
    @Override
    protected ReportTicketEntity createEntity() {
        return new ReportTicketEntity();
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
            ReportTicketEntity entity = new ReportTicketEntity();
            entity.setReporterUserId(100L);
            entity.setTargetType("USER");
            entity.setTargetId(200L);
            entity.setReason("骚扰");
            entity.setEvidence("{\"messages\":[\"msg1\"]}");
            entity.setStatus("PENDING");
            entity.setResult("已处理");

            assertThat(entity.getReporterUserId()).isEqualTo(100L);
            assertThat(entity.getTargetType()).isEqualTo("USER");
            assertThat(entity.getTargetId()).isEqualTo(200L);
            assertThat(entity.getReason()).isEqualTo("骚扰");
            assertThat(entity.getEvidence()).isEqualTo("{\"messages\":[\"msg1\"]}");
            assertThat(entity.getStatus()).isEqualTo("PENDING");
            assertThat(entity.getResult()).isEqualTo("已处理");
        }

        @Test
        @DisplayName("新创建的实体字段应为 null")
        void newEntityShouldHaveNullFields() {
            ReportTicketEntity entity = new ReportTicketEntity();
            assertThat(entity.getReporterUserId()).isNull();
            assertThat(entity.getTargetType()).isNull();
            assertThat(entity.getTargetId()).isNull();
            assertThat(entity.getReason()).isNull();
            assertThat(entity.getEvidence()).isNull();
            assertThat(entity.getStatus()).isNull();
            assertThat(entity.getResult()).isNull();
        }
    }

    @Nested
    @DisplayName("兼容字段映射 getEvidenceJson() / setEvidenceJson()")
    class CompatibleFieldMappingTest {
        @Test
        @DisplayName("getEvidenceJson() 应返回 evidence 的值")
        void getEvidenceJsonShouldReturnEvidence() {
            ReportTicketEntity entity = new ReportTicketEntity();
            entity.setEvidence("{\"key\":\"value\"}");
            assertThat(entity.getEvidenceJson()).isEqualTo("{\"key\":\"value\"}");
        }

        /**
         * 设置{@code evidenceJsonShouldSetEvidence}。
         */
        @Test
        @DisplayName("setEvidenceJson() 应设置 evidence")
        void setEvidenceJsonShouldSetEvidence() {
            ReportTicketEntity entity = new ReportTicketEntity();
            entity.setEvidenceJson("{\"data\":\"test\"}");
            assertThat(entity.getEvidence()).isEqualTo("{\"data\":\"test\"}");
        }

        @Test
        @DisplayName("getEvidenceJson() 和 getEvidence() 应始终一致")
        void jsonAndNormalGetterShouldAlwaysBeConsistent() {
            ReportTicketEntity entity = new ReportTicketEntity();
            entity.setEvidence("{\"a\":1}");
            assertThat(entity.getEvidenceJson()).isEqualTo(entity.getEvidence());

            entity.setEvidenceJson("{\"b\":2}");
            assertThat(entity.getEvidenceJson()).isEqualTo(entity.getEvidence());
        }

        @Test
        @DisplayName("evidence 为 null 时 getEvidenceJson() 应返回 null")
        void bothNullShouldReturnNull() {
            ReportTicketEntity entity = new ReportTicketEntity();
            assertThat(entity.getEvidenceJson()).isNull();
        }
    }

    @Nested
    @DisplayName("工单状态流转场景")
    class StatusFlowScenarioTest {
        @Test
        @DisplayName("举报工单初始状态为 PENDING")
        void initialStatusShouldBePending() {
            ReportTicketEntity entity = new ReportTicketEntity();
            entity.setReporterUserId(1L);
            entity.setTargetType("USER");
            entity.setTargetId(100L);
            entity.setStatus("PENDING");

            assertThat(entity.getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("处理中状态")
        void shouldSupportProcessingStatus() {
            ReportTicketEntity entity = new ReportTicketEntity();
            entity.setStatus("PROCESSING");
            assertThat(entity.getStatus()).isEqualTo("PROCESSING");
        }

        @Test
        @DisplayName("已解决状态")
        void shouldSupportResolvedStatus() {
            ReportTicketEntity entity = new ReportTicketEntity();
            entity.setStatus("RESOLVED");
            entity.setResult("已封禁用户");
            assertThat(entity.getStatus()).isEqualTo("RESOLVED");
            assertThat(entity.getResult()).isEqualTo("已封禁用户");
        }

        @Test
        @DisplayName("已拒绝状态")
        void shouldSupportRejectedStatus() {
            ReportTicketEntity entity = new ReportTicketEntity();
            entity.setStatus("REJECTED");
            entity.setResult("证据不足");
            assertThat(entity.getStatus()).isEqualTo("REJECTED");
            assertThat(entity.getResult()).isEqualTo("证据不足");
        }
    }
}
