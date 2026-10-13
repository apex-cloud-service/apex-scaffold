package com.apex.scaffold.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.model.record.AuditLogRecord;
import com.apex.scaffold.infrastructure.persistence.mapper.OperationLogMapper;
import com.apex.scaffold.infrastructure.persistence.po.OperationLogPO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 审计日志查询仓储实现测试。
 *
 * <p>锁定「写读同源」闭环契约：审计查询必须面向 {@code sys_operation_log}
 * （{@link OperationLogMapper}）分页查询，而非无人写入的 {@code audit_log} 表；
 * 并验证 PO → 领域记录字段映射（含状态码 0/1 → SUCCESS/FAILED）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogQueryRepositoryImpl 审计日志查询仓储测试")
class AuditLogQueryRepositoryImplTest {

    @Mock
    private OperationLogMapper operationLogMapper;

    @InjectMocks
    private AuditLogQueryRepositoryImpl repository;

    /** 构造一条写入 sys_operation_log 的 PO 样例（与 OperationLogAspect 落库口径一致）。 */
    private OperationLogPO samplePo(Long id, Integer status) {
        OperationLogPO po = new OperationLogPO();
        po.setId(id);
        po.setModule("用户管理");
        po.setOperationType("INSERT");
        po.setDescription("后台创建用户");
        po.setRequestMethod("POST");
        po.setRequestUri("/admin-api/v1/staff/users");
        po.setRequestParams("{\"username\":\"test\"}");
        po.setResponseResult("{\"id\":1}");
        po.setStatus(status);
        po.setExecutionTime(42L);
        po.setIp("127.0.0.1");
        po.setUserId(1001L);
        po.setUserName("张三");
        po.setCreateTime(LocalDateTime.of(2026, 8, 27, 10, 0));
        return po;
    }

    @Test
    @DisplayName("查询走 sys_operation_log 分页并映射成功状态记录")
    void searchShouldQueryOperationLogTableAndMapSuccessRecord() {
        Page<OperationLogPO> page = new Page<>(1, 20);
        page.setTotal(1);
        page.setRecords(List.of(samplePo(1L, 0)));
        when(operationLogMapper.selectPage(any(Page.class), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Page<OperationLogPO> arg = inv.getArgument(0);
            arg.setTotal(1);
            arg.setRecords(List.of(samplePo(1L, 0)));
            return arg;
        });

        Mono<PageResponse<AuditLogRecord>> result = repository.search("用户管理", "张三", 1, 20);

        StepVerifier.create(result)
                .assertNext(resp -> {
                    assertThat(resp.getTotal()).isEqualTo(1);
                    assertThat(resp.getList()).hasSize(1);
                    AuditLogRecord r = resp.getList().get(0);
                    assertThat(r.getId()).isEqualTo(1L);
                    assertThat(r.getOperatorId()).isEqualTo("1001");
                    assertThat(r.getOperatorName()).isEqualTo("张三");
                    assertThat(r.getOperatorIp()).isEqualTo("127.0.0.1");
                    assertThat(r.getModule()).isEqualTo("用户管理");
                    assertThat(r.getOperationType()).isEqualTo("INSERT");
                    assertThat(r.getMethod()).isEqualTo("POST");
                    assertThat(r.getRequestUrl()).isEqualTo("/admin-api/v1/staff/users");
                    assertThat(r.getRequestParams()).contains("username");
                    assertThat(r.getResponseResult()).contains("\"id\":1");
                    assertThat(r.getDuration()).isEqualTo(42L);
                    assertThat(r.getStatus()).isEqualTo("SUCCESS");
                    assertThat(r.getCreateTime()).isEqualTo(LocalDateTime.of(2026, 8, 27, 10, 0));
                })
                .verifyComplete();

        verify(operationLogMapper).selectPage(any(Page.class), any());
    }

    @Test
    @DisplayName("失败状态记录映射为 FAILED，空状态保持为空")
    void searchShouldMapFailedAndNullStatus() {
        when(operationLogMapper.selectPage(any(Page.class), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Page<OperationLogPO> arg = inv.getArgument(0);
            arg.setTotal(2);
            arg.setRecords(List.of(samplePo(2L, 1), samplePo(3L, null)));
            return arg;
        });

        StepVerifier.create(repository.search(null, null, 1, 20))
                .assertNext(resp -> {
                    assertThat(resp.getList()).hasSize(2);
                    assertThat(resp.getList().get(0).getStatus()).isEqualTo("FAILED");
                    assertThat(resp.getList().get(1).getStatus()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("每页大小被限制在 1-100 之间")
    void searchShouldBoundPageSize() {
        when(operationLogMapper.selectPage(any(Page.class), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Page<OperationLogPO> arg = inv.getArgument(0);
            arg.setTotal(0);
            arg.setRecords(List.of());
            return arg;
        });

        StepVerifier.create(repository.search(null, null, 1, 999))
                .assertNext(resp -> assertThat(resp.getPageSize()).isEqualTo(100))
                .verifyComplete();
    }
}
