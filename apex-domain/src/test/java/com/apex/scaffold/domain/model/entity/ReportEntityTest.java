package com.apex.scaffold.domain.model.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ReportEntity} 的单元测试类
 *
 * <p>测试报告的创建、状态流转（开始处理、更新进度、完成、失败）、状态判断等核心领域行为
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class ReportEntityTest {

    @Nested
    @DisplayName("静态工厂方法 createPhotoRiskReport()")
    class CreatePhotoRiskReportTest {

        /**
         * 执行 {@code withValidParamsShouldCreatePendingReport} 操作。
         */
        @Test
        @DisplayName("使用有效参数应创建待处理报告并设置正确初始状态")
        void withValidParamsShouldCreatePendingReport() {
            Long ownerUserId = 1L;
            Long photoId = 100L;

            ReportEntity report = ReportEntity.createPhotoRiskReport(ownerUserId, photoId);

            assertThat(report.getReportType()).isEqualTo(ReportEntity.ReportType.PHOTO_RISK.getCode());
            assertThat(report.getOwnerUserId()).isEqualTo(ownerUserId);
            assertThat(report.getPhotoId()).isEqualTo(photoId);
            assertThat(report.getStatus()).isEqualTo(ReportEntity.ReportStatus.PENDING.getCode());
            assertThat(report.getProgress()).isEqualTo(0);
            assertThat(report.getDataJson()).isNull();
            assertThat(report.getCreatedAt()).isNull();
            assertThat(report.isCompleted()).isFalse();
            assertThat(report.isFailed()).isFalse();
            assertThat(report.getCreateTime()).isNotNull();
            assertThat(report.getUpdateTime()).isNotNull();
            assertThat(report.isDeleted()).isFalse();
        }

        /**
         * 创建{@code timeShouldEqualUpdateTime}。
         */
        @Test
        @DisplayName("创建时间应与更新时间一致")
        void createTimeShouldEqualUpdateTime() {
            ReportEntity report = ReportEntity.createPhotoRiskReport(1L, 100L);
            assertThat(report.getCreateTime()).isEqualTo(report.getUpdateTime());
        }
    }

    @Nested
    @DisplayName("开始处理 startProcessing()")
    class StartProcessingTest {

        /**
         * 执行 {@code shouldSetStatusToRunningAndResetProgress} 操作。
         */
        @Test
        @DisplayName("应将状态设为 RUNNING 并重置进度为 0")
        void shouldSetStatusToRunningAndResetProgress() {
            ReportEntity report = ReportEntity.createPhotoRiskReport(1L, 100L);
            report.startProcessing();

            assertThat(report.getStatus()).isEqualTo(ReportEntity.ReportStatus.RUNNING.getCode());
            assertThat(report.getProgress()).isEqualTo(0);
        }

        /**
         * 执行 {@code shouldTouchUpdateTime} 操作。
         */
        @Test
        @DisplayName("应刷新更新时间")
        void shouldTouchUpdateTime() {
            ReportEntity report = ReportEntity.createPhotoRiskReport(1L, 100L);
            var originalUpdateTime = report.getUpdateTime();
            report.startProcessing();
            assertThat(report.getUpdateTime()).isAfterOrEqualTo(originalUpdateTime);
        }
    }

    @Nested
    @DisplayName("更新进度 updateProgress()")
    class UpdateProgressTest {

        /**
         * 执行 {@code shouldSetProgressValue} 操作。
         */
        @Test
        @DisplayName("应正确设置进度值")
        void shouldSetProgressValue() {
            ReportEntity report = ReportEntity.createPhotoRiskReport(1L, 100L);
            report.updateProgress(50);

            assertThat(report.getProgress()).isEqualTo(50);
        }

        /**
         * 执行 {@code progressShouldNotBeLessThanZero} 操作。
         */
        @Test
        @DisplayName("进度值不应小于 0")
        void progressShouldNotBeLessThanZero() {
            ReportEntity report = ReportEntity.createPhotoRiskReport(1L, 100L);
            report.updateProgress(-10);

            assertThat(report.getProgress()).isEqualTo(0);
        }

        /**
         * 执行 {@code progressShouldNotExceedOneHundred} 操作。
         */
        @Test
        @DisplayName("进度值不应大于 100")
        void progressShouldNotExceedOneHundred() {
            ReportEntity report = ReportEntity.createPhotoRiskReport(1L, 100L);
            report.updateProgress(150);

            assertThat(report.getProgress()).isEqualTo(100);
        }

        /**
         * 执行 {@code shouldTouchUpdateTime} 操作。
         */
        @Test
        @DisplayName("应刷新更新时间")
        void shouldTouchUpdateTime() {
            ReportEntity report = ReportEntity.createPhotoRiskReport(1L, 100L);
            var originalUpdateTime = report.getUpdateTime();
            report.updateProgress(80);
            assertThat(report.getUpdateTime()).isAfterOrEqualTo(originalUpdateTime);
        }
    }

    @Nested
    @DisplayName("完成处理 complete()")
    class CompleteTest {

        /**
         * 执行 {@code shouldSetStatusToDoneAndRecordData} 操作。
         */
        @Test
        @DisplayName("应将状态设为 DONE、进度设为 100 并设置数据和完成时间")
        void shouldSetStatusToDoneAndRecordData() {
            ReportEntity report = ReportEntity.createPhotoRiskReport(1L, 100L);
            String dataJson = "{\"risk\":\"low\"}";
            report.complete(dataJson);

            assertThat(report.getStatus()).isEqualTo(ReportEntity.ReportStatus.DONE.getCode());
            assertThat(report.getProgress()).isEqualTo(100);
            assertThat(report.getDataJson()).isEqualTo(dataJson);
            assertThat(report.getCreatedAt()).isNotNull();
            assertThat(report.isCompleted()).isTrue();
            assertThat(report.isFailed()).isFalse();
        }

        /**
         * 执行 {@code shouldTouchUpdateTime} 操作。
         */
        @Test
        @DisplayName("应刷新更新时间")
        void shouldTouchUpdateTime() {
            ReportEntity report = ReportEntity.createPhotoRiskReport(1L, 100L);
            var originalUpdateTime = report.getUpdateTime();
            report.complete("{}");
            assertThat(report.getUpdateTime()).isAfterOrEqualTo(originalUpdateTime);
        }
    }

    @Nested
    @DisplayName("标记失败 markAsFailed()")
    class MarkAsFailedTest {

        /**
         * 执行 {@code shouldSetStatusToFailed} 操作。
         */
        @Test
        @DisplayName("应将状态设为 FAILED")
        void shouldSetStatusToFailed() {
            ReportEntity report = ReportEntity.createPhotoRiskReport(1L, 100L);
            report.markAsFailed();

            assertThat(report.getStatus()).isEqualTo(ReportEntity.ReportStatus.FAILED.getCode());
            assertThat(report.isCompleted()).isFalse();
            assertThat(report.isFailed()).isTrue();
        }

        /**
         * 执行 {@code shouldTouchUpdateTime} 操作。
         */
        @Test
        @DisplayName("应刷新更新时间")
        void shouldTouchUpdateTime() {
            ReportEntity report = ReportEntity.createPhotoRiskReport(1L, 100L);
            var originalUpdateTime = report.getUpdateTime();
            report.markAsFailed();
            assertThat(report.getUpdateTime()).isAfterOrEqualTo(originalUpdateTime);
        }
    }

    @Nested
    @DisplayName("状态判断 isCompleted() / isFailed()")
    class StatusCheckTest {

        /**
         * 执行 {@code pendingReportShouldReturnFalseForIsCompleted} 操作。
         */
        @Test
        @DisplayName("待处理报告 isCompleted() 应返回 false")
        void pendingReportShouldReturnFalseForIsCompleted() {
            ReportEntity report = ReportEntity.createPhotoRiskReport(1L, 100L);
            assertThat(report.isCompleted()).isFalse();
            assertThat(report.isFailed()).isFalse();
        }

        /**
         * 执行 {@code completedReportShouldReturnTrueForIsCompleted} 操作。
         */
        @Test
        @DisplayName("完成后 isCompleted() 应返回 true")
        void completedReportShouldReturnTrueForIsCompleted() {
            ReportEntity report = ReportEntity.createPhotoRiskReport(1L, 100L);
            report.complete("{}");
            assertThat(report.isCompleted()).isTrue();
            assertThat(report.isFailed()).isFalse();
        }

        /**
         * 执行 {@code failedReportShouldReturnTrueForIsFailed} 操作。
         */
        @Test
        @DisplayName("失败后 isFailed() 应返回 true")
        void failedReportShouldReturnTrueForIsFailed() {
            ReportEntity report = ReportEntity.createPhotoRiskReport(1L, 100L);
            report.markAsFailed();
            assertThat(report.isCompleted()).isFalse();
            assertThat(report.isFailed()).isTrue();
        }

        /**
         * 执行 {@code runningReportShouldReturnFalseForIsCompleted} 操作。
         */
        @Test
        @DisplayName("处理中状态 isCompleted() 应返回 false")
        void runningReportShouldReturnFalseForIsCompleted() {
            ReportEntity report = ReportEntity.createPhotoRiskReport(1L, 100L);
            report.startProcessing();
            assertThat(report.isCompleted()).isFalse();
            assertThat(report.isFailed()).isFalse();
        }
    }

    @Nested
    @DisplayName("setter 方法")
    class SetterTest {

        /**
         * 设置{@code tersShouldUpdateFields}。
         */
        @Test
        @DisplayName("使用 setter 设置属性应正确更新")
        void settersShouldUpdateFields() {
            ReportEntity report = new ReportEntity();
            report.setReportType(ReportEntity.ReportType.PHOTO_RISK.getCode());
            report.setOwnerUserId(10L);
            report.setPhotoId(20L);
            report.setStatus(ReportEntity.ReportStatus.DONE.getCode());
            report.setProgress(100);
            report.setDataJson("{}");
            var now = java.time.LocalDateTime.now();
            report.setCreatedAt(now);

            assertThat(report.getReportType()).isEqualTo(ReportEntity.ReportType.PHOTO_RISK.getCode());
            assertThat(report.getOwnerUserId()).isEqualTo(10L);
            assertThat(report.getPhotoId()).isEqualTo(20L);
            assertThat(report.getStatus()).isEqualTo(ReportEntity.ReportStatus.DONE.getCode());
            assertThat(report.getProgress()).isEqualTo(100);
            assertThat(report.getDataJson()).isEqualTo("{}");
            assertThat(report.getCreatedAt()).isEqualTo(now);
        }
    }

    @Nested
    @DisplayName("ReportType 枚举")
    class ReportTypeEnumTest {

        /**
         * 执行 {@code photoRiskCodeShouldBePhotoRisk} 操作。
         */
        @Test
        @DisplayName("PHOTO_RISK 的 code 应为 PHOTO_RISK")
        void photoRiskCodeShouldBePhotoRisk() {
            assertThat(ReportEntity.ReportType.PHOTO_RISK.getCode()).isEqualTo("PHOTO_RISK");
        }
    }

    @Nested
    @DisplayName("ReportStatus 枚举")
    class ReportStatusEnumTest {

        /**
         * 执行 {@code pendingCodeShouldBePending} 操作。
         */
        @Test
        @DisplayName("PENDING 的 code 应为 PENDING")
        void pendingCodeShouldBePending() {
            assertThat(ReportEntity.ReportStatus.PENDING.getCode()).isEqualTo("PENDING");
        }

        /**
         * 执行 {@code runningCodeShouldBeRunning} 操作。
         */
        @Test
        @DisplayName("RUNNING 的 code 应为 RUNNING")
        void runningCodeShouldBeRunning() {
            assertThat(ReportEntity.ReportStatus.RUNNING.getCode()).isEqualTo("RUNNING");
        }

        /**
         * 执行 {@code doneCodeShouldBeDone} 操作。
         */
        @Test
        @DisplayName("DONE 的 code 应为 DONE")
        void doneCodeShouldBeDone() {
            assertThat(ReportEntity.ReportStatus.DONE.getCode()).isEqualTo("DONE");
        }

        /**
         * 执行 {@code failedCodeShouldBeFailed} 操作。
         */
        @Test
        @DisplayName("FAILED 的 code 应为 FAILED")
        void failedCodeShouldBeFailed() {
            assertThat(ReportEntity.ReportStatus.FAILED.getCode()).isEqualTo("FAILED");
        }
    }
}
