package com.apex.scaffold.authcenter.application.service.impl;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.authcenter.domain.model.entity.AuditLogEntity;
import com.apex.scaffold.authcenter.domain.repository.AuditLogRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogServiceImpl 单元测试")
class AuditLogServiceImplTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuditLogServiceImpl(auditLogRepository);
    }

    private AuditLogEntity savedEntity(AuditLogEntity input) {
        input.setId(1L);
        return input;
    }

    @Nested
    @DisplayName("log() 方法测试")
    class LogTest {

        @Test
        @DisplayName("保存审计日志成功，自动填充事件时间")
        void testLog_Success() {
            when(auditLogRepository.save(any(AuditLogEntity.class)))
                    .thenAnswer(invocation -> Mono.just(savedEntity(invocation.getArgument(0))));

            AuditLogEntity entity = AuditLogEntity.builder()
                    .eventType("LOGIN")
                    .userId(1L)
                    .tenantId("tenant-1")
                    .build();

            StepVerifier.create(service.log(entity))
                    .assertNext(saved -> {
                        org.assertj.core.api.Assertions.assertThat(saved.getId())
                                .isEqualTo(1L);
                        org.assertj.core.api.Assertions.assertThat(saved.getEventTime())
                                .isNotNull();
                        org.assertj.core.api.Assertions.assertThat(saved.getCreatedAt())
                                .isNotNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("保存失败时传播错误")
        void testLog_Failure() {
            when(auditLogRepository.save(any(AuditLogEntity.class)))
                    .thenReturn(Mono.error(new RuntimeException("db error")));

            AuditLogEntity entity = AuditLogEntity.builder().eventType("LOGIN").build();

            StepVerifier.create(service.log(entity))
                    .expectError(RuntimeException.class)
                    .verify();
        }

        @Test
        @DisplayName("默认方法：通用日志记录，userId为合法数字")
        void testDefaultLog_ValidUserId() {
            when(auditLogRepository.save(any(AuditLogEntity.class)))
                    .thenAnswer(invocation -> Mono.just(savedEntity(invocation.getArgument(0))));

            StepVerifier.create(service.log("LOGIN", "123", "tenant-1", "login action", "res-1", null))
                    .verifyComplete();

            verify(auditLogRepository).save(argThat(e -> e.getUserId() != null && e.getUserId() == 123L));
        }

        @Test
        @DisplayName("默认方法：userId为非数字时保持为null")
        void testDefaultLog_NonNumericUserId() {
            when(auditLogRepository.save(any(AuditLogEntity.class)))
                    .thenAnswer(invocation -> Mono.just(savedEntity(invocation.getArgument(0))));

            StepVerifier.create(service.log("LOGIN", "not-a-number", "tenant-1", "login action", "res-1", null))
                    .verifyComplete();

            verify(auditLogRepository).save(argThat(e -> e.getUserId() == null));
        }
    }

    @Nested
    @DisplayName("事件专用记录方法测试")
    class EventSpecificLogTest {

        @BeforeEach
        void mockSave() {
            when(auditLogRepository.save(any(AuditLogEntity.class)))
                    .thenAnswer(invocation -> Mono.just(savedEntity(invocation.getArgument(0))));
        }

        @Test
        @DisplayName("logLogin - 成功登录风险等级为LOW")
        void testLogLogin_Success() {
            StepVerifier.create(service.logLogin(1L, "user1", "SUCCESS", "127.0.0.1", "UA", "tenant-1", null))
                    .assertNext(saved -> org.assertj.core.api.Assertions.assertThat(saved.getRiskLevel())
                            .isEqualTo("LOW"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("logLogin - 失败登录风险等级为MEDIUM")
        void testLogLogin_Failure() {
            StepVerifier.create(service.logLogin(1L, "user1", "FAILURE", "127.0.0.1", "UA", "tenant-1", "bad password"))
                    .assertNext(saved -> org.assertj.core.api.Assertions.assertThat(saved.getRiskLevel())
                            .isEqualTo("MEDIUM"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("logLogout")
        void testLogLogout() {
            StepVerifier.create(service.logLogout(1L, "user1", "127.0.0.1", "tenant-1"))
                    .assertNext(saved -> org.assertj.core.api.Assertions.assertThat(saved.getEventType())
                            .isEqualTo("LOGOUT"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("logTokenIssued")
        void testLogTokenIssued() {
            StepVerifier.create(service.logTokenIssued(1L, "user1", "token-1", "127.0.0.1", "tenant-1"))
                    .assertNext(saved -> org.assertj.core.api.Assertions.assertThat(saved.getEventType())
                            .isEqualTo("TOKEN_ISSUED"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("logTokenRefreshed")
        void testLogTokenRefreshed() {
            StepVerifier.create(service.logTokenRefreshed(1L, "user1", "token-1", "127.0.0.1", "tenant-1"))
                    .assertNext(saved -> org.assertj.core.api.Assertions.assertThat(saved.getEventType())
                            .isEqualTo("TOKEN_REFRESHED"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("logTokenRevoked")
        void testLogTokenRevoked() {
            StepVerifier.create(service.logTokenRevoked(1L, "user1", "token-1", "127.0.0.1", "tenant-1", "logout"))
                    .assertNext(saved -> {
                        org.assertj.core.api.Assertions.assertThat(saved.getEventType())
                                .isEqualTo("TOKEN_REVOKED");
                        org.assertj.core.api.Assertions.assertThat(saved.getDetails())
                                .contains("logout");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("logPermissionChanged")
        void testLogPermissionChanged() {
            StepVerifier.create(service.logPermissionChanged(1L, "user1", "GRANT", "res-1", "127.0.0.1", "tenant-1"))
                    .assertNext(saved -> org.assertj.core.api.Assertions.assertThat(saved.getEventType())
                            .isEqualTo("PERMISSION_CHANGED"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("logRoleAssigned")
        void testLogRoleAssigned() {
            StepVerifier.create(service.logRoleAssigned(1L, "admin", 2L, 3L, "127.0.0.1", "tenant-1"))
                    .assertNext(saved -> {
                        org.assertj.core.api.Assertions.assertThat(saved.getEventType())
                                .isEqualTo("ROLE_ASSIGNED");
                        org.assertj.core.api.Assertions.assertThat(saved.getAction())
                                .contains("2");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("logSessionTerminated")
        void testLogSessionTerminated() {
            StepVerifier.create(
                            service.logSessionTerminated(1L, "user1", "session-1", "127.0.0.1", "tenant-1", "expired"))
                    .assertNext(saved -> org.assertj.core.api.Assertions.assertThat(saved.getEventType())
                            .isEqualTo("SESSION_TERMINATED"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("查询方法测试")
    class QueryTest {

        @Test
        @DisplayName("getLogById")
        void testGetLogById() {
            AuditLogEntity entity = AuditLogEntity.builder().id(1L).build();
            when(auditLogRepository.findById(1L)).thenReturn(Mono.just(entity));

            StepVerifier.create(service.getLogById(1L)).expectNext(entity).verifyComplete();
        }

        @Test
        @DisplayName("listUserLogs")
        void testListUserLogs() {
            AuditLogEntity entity = AuditLogEntity.builder().id(1L).build();
            when(auditLogRepository.findByUserId(1L, "tenant-1")).thenReturn(Flux.just(entity));

            StepVerifier.create(service.listUserLogs(1L, "tenant-1"))
                    .expectNext(entity)
                    .verifyComplete();
        }

        @Test
        @DisplayName("listLogsByEventType")
        void testListLogsByEventType() {
            AuditLogEntity entity = AuditLogEntity.builder().id(1L).build();
            when(auditLogRepository.findByEventType("LOGIN", "tenant-1")).thenReturn(Flux.just(entity));

            StepVerifier.create(service.listLogsByEventType("LOGIN", "tenant-1"))
                    .expectNext(entity)
                    .verifyComplete();
        }

        @Test
        @DisplayName("listLogsByTimeRange")
        void testListLogsByTimeRange() {
            LocalDateTime start = LocalDateTime.now().minusDays(1);
            LocalDateTime end = LocalDateTime.now();
            AuditLogEntity entity = AuditLogEntity.builder().id(1L).build();
            when(auditLogRepository.findByTimeRange(start, end, "tenant-1")).thenReturn(Flux.just(entity));

            StepVerifier.create(service.listLogsByTimeRange(start, end, "tenant-1"))
                    .expectNext(entity)
                    .verifyComplete();
        }

        @Test
        @DisplayName("listFailedLogs")
        void testListFailedLogs() {
            AuditLogEntity entity = AuditLogEntity.builder().id(1L).build();
            when(auditLogRepository.findFailedEvents("tenant-1")).thenReturn(Flux.just(entity));

            StepVerifier.create(service.listFailedLogs("tenant-1"))
                    .expectNext(entity)
                    .verifyComplete();
        }

        @Test
        @DisplayName("listHighRiskLogs")
        void testListHighRiskLogs() {
            AuditLogEntity entity = AuditLogEntity.builder().id(1L).build();
            when(auditLogRepository.findHighRiskEvents("tenant-1")).thenReturn(Flux.just(entity));

            StepVerifier.create(service.listHighRiskLogs("tenant-1"))
                    .expectNext(entity)
                    .verifyComplete();
        }

        @Test
        @DisplayName("listLogsByIpAddress")
        void testListLogsByIpAddress() {
            AuditLogEntity entity = AuditLogEntity.builder().id(1L).build();
            when(auditLogRepository.findByIpAddress("127.0.0.1", "tenant-1")).thenReturn(Flux.just(entity));

            StepVerifier.create(service.listLogsByIpAddress("127.0.0.1", "tenant-1"))
                    .expectNext(entity)
                    .verifyComplete();
        }

        @Test
        @DisplayName("countLogsByTimeRange")
        void testCountLogsByTimeRange() {
            LocalDateTime start = LocalDateTime.now().minusDays(1);
            LocalDateTime end = LocalDateTime.now();
            when(auditLogRepository.countByTimeRange(start, end, "tenant-1")).thenReturn(Mono.just(5L));

            StepVerifier.create(service.countLogsByTimeRange(start, end, "tenant-1"))
                    .expectNext(5L)
                    .verifyComplete();
        }

        @Test
        @DisplayName("countLoginFailures - 统计时间范围内的失败次数")
        void testCountLoginFailures() {
            LocalDateTime startTime = LocalDateTime.now().minusHours(1);

            AuditLogEntity failedInRange = AuditLogEntity.builder()
                    .eventType("LOGIN")
                    .eventStatus("FAILURE")
                    .eventTime(LocalDateTime.now().minusMinutes(30))
                    .build();

            AuditLogEntity successInRange = AuditLogEntity.builder()
                    .eventType("LOGIN")
                    .eventStatus("SUCCESS")
                    .eventTime(LocalDateTime.now().minusMinutes(20))
                    .build();

            AuditLogEntity failedOutOfRange = AuditLogEntity.builder()
                    .eventType("LOGIN")
                    .eventStatus("FAILURE")
                    .eventTime(LocalDateTime.now().minusHours(2))
                    .build();

            when(auditLogRepository.findByUserIdAndEventType(1L, "LOGIN", "tenant-1"))
                    .thenReturn(Flux.just(failedInRange, successInRange, failedOutOfRange));

            StepVerifier.create(service.countLoginFailures(1L, startTime, "tenant-1"))
                    .expectNext(1L)
                    .verifyComplete();
        }

        @Test
        @DisplayName("cleanupExpiredLogs")
        void testCleanupExpiredLogs() {
            LocalDateTime beforeTime = LocalDateTime.now().minusDays(30);
            when(auditLogRepository.deleteByCreatedAtBefore(beforeTime, "tenant-1"))
                    .thenReturn(Mono.just(10L));

            StepVerifier.create(service.cleanupExpiredLogs(beforeTime, "tenant-1"))
                    .expectNext(10L)
                    .verifyComplete();
        }
    }
}
