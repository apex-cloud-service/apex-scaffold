package com.apex.scaffold.officeservice.infrastructure.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.office.domain.approval.ApprovalCancelOperation;
import com.apex.scaffold.office.domain.approval.ApprovalCancelOperationRepository;
import com.apex.scaffold.office.domain.approval.ApprovalCancelStatus;
import com.apex.scaffold.office.infrastructure.mapper.ApprovalCancelOperationMapper;
import com.apex.scaffold.office.infrastructure.mapper.ApprovalProcessMapper;
import com.apex.scaffold.office.infrastructure.po.ApprovalCancelOperationPO;
import com.apex.scaffold.office.infrastructure.po.ApprovalProcessPO;
import com.apex.scaffold.office.infrastructure.repository.ApprovalCancelOperationRepositoryImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

/**
 * 审批撤销编排状态仓储集成测试（basic Profile / H2）。
 *
 * <p>以 basic Profile 全量装配上下文，通过真实 MyBatis-Plus Mapper 与同步事务验证
 * {@link ApprovalCancelOperationRepositoryImpl} 的落库语义：
 * <ul>
 *   <li>编排记录保存与幂等键查询回读（含枚举状态映射、租户隔离）；</li>
 *   <li>(tenant_id, idempotency_key, deleted) 唯一键对重复撤销请求的拒绝（幂等重放的落库前提）；</li>
 *   <li>{@code completeCancel} 补偿事务：同事务更新审批流程与编排记录、乐观锁版本推进；</li>
 *   <li>流程不存在时补偿事务报错（fail-closed，不静默成功）。</li>
 * </ul>
 *
 * <p>H2 为测试专用内存库，测试数据按本测试租户与前缀清理，不影响其他用例。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            // apex-common ConfigEncryptionService 要求恰好 32 字节的配置加密密钥
            "security.config.encryption.key=01234567890123456789012345678901",
            // 网关仅供独立联合测试装配，非 Web 的办公上下文不加载其自动配置。
            "spring.cloud.gateway.enabled=false",
            "spring.autoconfigure.exclude=org.springframework.cloud.gateway.config.GatewayRedisAutoConfiguration"
        })
@ActiveProfiles("basic")
@DisplayName("审批撤销编排状态仓储集成测试")
class ApprovalCancelOperationRepositoryIntegrationTest {

    /** 本测试专用租户：隔离于 basic-schema 空库，清理时按租户删除。 */
    private static final Long TENANT_ID = 990001L;

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private ApprovalCancelOperationRepository repository;

    @Autowired
    private ApprovalProcessMapper approvalProcessMapper;

    @Autowired
    private ApprovalCancelOperationMapper operationMapper;

    @AfterEach
    void cleanUp() {
        approvalProcessMapper.delete(
                Wrappers.<ApprovalProcessPO>lambdaQuery().eq(ApprovalProcessPO::getTenantId, TENANT_ID));
        operationMapper.delete(Wrappers.<ApprovalCancelOperationPO>lambdaQuery()
                .eq(ApprovalCancelOperationPO::getTenantId, TENANT_ID));
    }

    private ApprovalProcessPO insertProcess(String processInstanceId, String status, int version) {
        ApprovalProcessPO process = new ApprovalProcessPO();
        process.setTenantId(TENANT_ID);
        process.setPlatform("dingtalk");
        process.setProcessInstanceId(processInstanceId);
        process.setProcessName("撤销补偿测试流程");
        process.setInitiatorId("1001");
        process.setInitiatorName("测试发起人");
        process.setStatus(status);
        process.setVersion(version);
        process.setDeleted(false);
        process.setCreatedAt(LocalDateTime.now());
        process.setUpdatedAt(LocalDateTime.now());
        approvalProcessMapper.insert(process);
        return process;
    }

    private ApprovalCancelOperationPO insertOperation(String processInstanceId, String idempotencyKey, String status) {
        ApprovalCancelOperationPO operation = new ApprovalCancelOperationPO();
        operation.setTenantId(TENANT_ID);
        operation.setProcessInstanceId(processInstanceId);
        operation.setIdempotencyKey(idempotencyKey);
        operation.setStatus(status);
        operation.setCreateTime(LocalDateTime.now());
        operation.setUpdateTime(LocalDateTime.now());
        operation.setVersion(0);
        operation.setDeleted(false);
        operationMapper.insert(operation);
        return operation;
    }

    @Test
    @DisplayName("编排记录保存后可按幂等键回读且状态映射正确")
    void saveAndFindRoundTripMapsStatus() {
        ApprovalCancelOperation pending =
                ApprovalCancelOperation.pending(TENANT_ID, "PI-ROUNDTRIP", "idem-roundtrip-1");

        StepVerifier.create(repository.save(pending))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getStatus()).isEqualTo(ApprovalCancelStatus.PENDING);
                })
                .expectComplete()
                .verify(TIMEOUT);

        StepVerifier.create(repository.findByTenantAndKey(TENANT_ID, "idem-roundtrip-1"))
                .assertNext(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getProcessInstanceId()).isEqualTo("PI-ROUNDTRIP");
                    assertThat(found.getStatus()).isEqualTo(ApprovalCancelStatus.PENDING);
                })
                .expectComplete()
                .verify(TIMEOUT);

        // 未命中语义：空完成（不下发 null 值）
        StepVerifier.create(repository.findByTenantAndKey(TENANT_ID + 1, "idem-roundtrip-1"))
                .verifyComplete();
    }

    @Test
    @DisplayName("同租户重复幂等键被唯一键拒绝（幂等重放的落库前提）")
    void duplicateIdempotencyKeyRejectedByUniqueIndex() {
        ApprovalCancelOperation first = ApprovalCancelOperation.pending(TENANT_ID, "PI-DUP", "idem-dup-1");
        ApprovalCancelOperation second = ApprovalCancelOperation.pending(TENANT_ID, "PI-DUP-2", "idem-dup-1");

        StepVerifier.create(repository.save(first))
                .assertNext(saved -> assertThat(saved.getId()).isNotNull())
                .expectComplete()
                .verify(TIMEOUT);
        StepVerifier.create(repository.save(second))
                .expectError(DataIntegrityViolationException.class)
                .verify(TIMEOUT);
    }

    @Test
    @DisplayName("补偿事务同事务更新流程与编排记录并推进乐观锁版本")
    void completeCancelUpdatesProcessAndOperationAtomically() {
        ApprovalProcessPO process = insertProcess("PI-COMPLETE", "APPROVING", 0);
        ApprovalCancelOperationPO operation = insertOperation("PI-COMPLETE", "idem-complete-1", "REMOTE_CANCELLED");

        StepVerifier.create(repository.completeCancel(TENANT_ID, "PI-COMPLETE", operation.getId()))
                .assertNext(result -> assertThat(result).isTrue())
                .expectComplete()
                .verify(TIMEOUT);

        ApprovalProcessPO reloaded = approvalProcessMapper.selectByProcessInstanceId("PI-COMPLETE", TENANT_ID);
        assertThat(reloaded.getStatus()).isEqualTo("CANCELLED");
        assertThat(reloaded.getCompletedAt()).isNotNull();
        assertThat(reloaded.getVersion()).isEqualTo(process.getVersion() + 1);

        ApprovalCancelOperationPO reloadedOperation = operationMapper.selectById(operation.getId());
        assertThat(reloadedOperation.getStatus()).isEqualTo(ApprovalCancelStatus.COMPLETED.name());
    }

    @Test
    @DisplayName("流程不存在时补偿事务报错且不落任何状态")
    void completeCancelFailsWhenProcessMissing() {
        ApprovalCancelOperationPO operation = insertOperation("PI-MISSING", "idem-missing-1", "REMOTE_CANCELLED");

        StepVerifier.create(repository.completeCancel(TENANT_ID, "PI-MISSING", operation.getId()))
                .expectError(IllegalStateException.class)
                .verify(TIMEOUT);

        ApprovalCancelOperationPO reloaded = operationMapper.selectById(operation.getId());
        assertThat(reloaded.getStatus()).isEqualTo(ApprovalCancelStatus.REMOTE_CANCELLED.name());
    }

    @Test
    @DisplayName("远端已撤销阶段是重放成功的编排状态口径")
    void remoteCancelledOperationIsReplayableAsSuccess() {
        ApprovalCancelOperation remoteCancelled =
                ApprovalCancelOperation.pending(TENANT_ID, "PI-REPLAY", "idem-replay-1");
        remoteCancelled.markRemoteCancelled();

        assertThat(remoteCancelled.isReplayableAsSuccess()).isTrue();
        assertThat(ApprovalCancelStatus.PENDING.isReplayableAsSuccess()).isFalse();

        List<String> statusNames =
                Arrays.stream(ApprovalCancelStatus.values()).map(Enum::name).collect(Collectors.toList());
        assertThat(statusNames).containsExactly("PENDING", "REMOTE_CANCELLED", "COMPLETED", "FAILED");
    }
}
