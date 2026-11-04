package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.config.CapabilityUsageProperties;
import com.apex.scaffold.application.config.OverviewProperties;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.repository.CapabilityUsageQueryRepository;
import com.apex.scaffold.domain.repository.CapabilityUsageQueryRepository.TenantUsageCount;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 能力调用用量查询服务单元测试（方案 D4 计量）。
 *
 * <p>覆盖能力编码映射校验、时间区间缺省与上限、租户范围解析
 * （显式参数 / 租户上下文回退）与内存分页语义。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("能力调用用量查询服务测试")
class CapabilityUsageServiceTest {

    @Mock
    private CapabilityUsageQueryRepository capabilityUsageQueryRepository;

    private CapabilityUsageService capabilityUsageService;

    /** 桩指标客户端：默认无指标，可按能力/租户投喂数值。 */
    private CapabilityMetricClient metricClient;

    @BeforeEach
    void setUp() {
        CapabilityUsageProperties properties = new CapabilityUsageProperties();
        metricClient =
                new CapabilityMetricClient(
                        properties,
                        new OverviewProperties(),
                        new com.apex.scaffold.common.internal.InternalAuthProperties(),
                        null) {
                    @Override
                    public Mono<Long> fetch(String capability, String tenantId) {
                        return stubMetric(capability, tenantId);
                    }
                };
        capabilityUsageService = new CapabilityUsageService(capabilityUsageQueryRepository, properties, metricClient);
    }

    /** 测试投喂的存量指标：{@code capability:tenant} → 数值。 */
    private final java.util.Map<String, Long> metrics = new java.util.concurrent.ConcurrentHashMap<>();

    private Mono<Long> stubMetric(String capability, String tenantId) {
        Long value = metrics.get(capability + ":" + tenantId);
        return value == null ? Mono.empty() : Mono.just(value);
    }

    @Test
    @DisplayName("显式租户 + 已配置能力：聚合行映射为 DTO 且无下一页")
    void queryUsage_scopedTenant_success() {
        when(capabilityUsageQueryRepository.countCallsByUriPrefixes(anyList(), any(), any(), eq("T001")))
                .thenReturn(Mono.just(List.of(new TenantUsageCount("T001", 5L))));

        StepVerifier.create(capabilityUsageService.queryUsage("T001", "file-storage", null, null, 1, 20))
                .assertNext(page -> {
                    assertThat(page.getTotal()).isEqualTo(1L);
                    assertThat(page.getList()).hasSize(1);
                    assertThat(page.getList().get(0).getTenantId()).isEqualTo("T001");
                    assertThat(page.getList().get(0).getCapability()).isEqualTo("file-storage");
                    assertThat(page.getList().get(0).getCallCount()).isEqualTo(5L);
                    assertThat(page.getHasNext()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("单租户查询应补充存量维度（D4 四维度计量）")
    void queryUsage_scopedTenant_attachesStockMetric() {
        metrics.put("file-storage:T001", 2048L);
        when(capabilityUsageQueryRepository.countCallsByUriPrefixes(anyList(), any(), any(), eq("T001")))
                .thenReturn(Mono.just(List.of(new TenantUsageCount("T001", 5L))));

        StepVerifier.create(capabilityUsageService.queryUsage("T001", "file-storage", null, null, 1, 20))
                .assertNext(page -> {
                    assertThat(page.getList().get(0).getCallCount()).isEqualTo(5L);
                    assertThat(page.getList().get(0).getStorageUsageBytes()).isEqualTo(2048L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("平台全量视角不填充存量维度")
    void queryUsage_platformView_skipsStockMetric() {
        metrics.put("file-storage:*", 999L);
        when(capabilityUsageQueryRepository.countCallsByUriPrefixes(anyList(), any(), any(), isNull()))
                .thenReturn(Mono.just(List.of(new TenantUsageCount("T001", 3L), new TenantUsageCount("T002", 4L))));

        StepVerifier.create(capabilityUsageService.queryUsage(null, "file-storage", null, null, 1, 20))
                .assertNext(page -> {
                    assertThat(page.getList()).allSatisfy(row -> assertThat(row.getStorageUsageBytes())
                            .isNull());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("存量指标源失败时按源降级为 null，不影响调用次数")
    void queryUsage_metricFailure_degradesGracefully() {
        when(capabilityUsageQueryRepository.countCallsByUriPrefixes(anyList(), any(), any(), eq("T001")))
                .thenReturn(Mono.just(List.of(new TenantUsageCount("T001", 5L))));

        StepVerifier.create(capabilityUsageService.queryUsage("T001", "file-storage", null, null, 1, 20))
                .assertNext(page -> {
                    assertThat(page.getList().get(0).getCallCount()).isEqualTo(5L);
                    assertThat(page.getList().get(0).getStorageUsageBytes()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("未配置调用映射但已登记的能力应返回零调用行并补充存量指标")
    void queryUsage_catalogCapabilityWithoutMapping_zeroRowWithStock() {
        metrics.put("iot-device:T001", 7L);

        StepVerifier.create(capabilityUsageService.queryUsage("T001", "iot-device", null, null, 1, 20))
                .assertNext(page -> {
                    assertThat(page.getTotal()).isEqualTo(1L);
                    assertThat(page.getList()).hasSize(1);
                    assertThat(page.getList().get(0).getCallCount()).isZero();
                    assertThat(page.getList().get(0).getDeviceCount()).isEqualTo(7L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("平台全量视角下未配置映射的能力不生成行")
    void queryUsage_catalogCapabilityWithoutMapping_platformView() {
        StepVerifier.create(capabilityUsageService.queryUsage(null, "iot-device", null, null, 1, 20))
                .assertNext(page -> assertThat(page.getList()).isEmpty())
                .verifyComplete();
    }

    @Test
    @DisplayName("目录中已登记但未通过目录校验的能力编码应拒绝")
    void queryUsage_catalogUnknownCapability() {
        StepVerifier.create(capabilityUsageService.queryUsage("T001", "not-in-catalog", null, null, 1, 20))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(BusinessException.class)
                        .extracting(e -> ((BusinessException) e).getCode())
                        .isEqualTo(ResultCode.PARAM_ERROR.getCode()))
                .verify();
    }

    @Test
    @DisplayName("未知能力编码返回 PARAM_ERROR 且不触发仓储查询")
    void queryUsage_unknownCapability() {
        StepVerifier.create(capabilityUsageService.queryUsage(null, "unknown-capability", null, null, 1, 20))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(BusinessException.class)
                        .extracting(e -> ((BusinessException) e).getCode())
                        .isEqualTo(ResultCode.PARAM_ERROR.getCode()))
                .verify();

        verifyNoInteractions(capabilityUsageQueryRepository);
    }

    @Test
    @DisplayName("from 晚于 to 返回 PARAM_ERROR")
    void queryUsage_fromAfterTo() {
        LocalDate from = LocalDate.of(2026, 2, 1);
        LocalDate to = LocalDate.of(2026, 1, 1);

        StepVerifier.create(capabilityUsageService.queryUsage(null, "workflow", from, to, 1, 20))
                .expectErrorSatisfies(error ->
                        assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode()))
                .verify();

        verifyNoInteractions(capabilityUsageQueryRepository);
    }

    @Test
    @DisplayName("时间跨度超过上限返回 PARAM_ERROR")
    void queryUsage_rangeExceeded() {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(120);

        StepVerifier.create(capabilityUsageService.queryUsage(null, "workflow", from, to, 1, 20))
                .expectErrorSatisfies(error ->
                        assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode()))
                .verify();

        verifyNoInteractions(capabilityUsageQueryRepository);
    }

    @Test
    @DisplayName("缺省口径：近 7 天（含当天）、页大小 20，多租户结果内存分页且 hasNext 正确")
    void queryUsage_defaultRangeAndPaging() {
        List<TenantUsageCount> rows = IntStream.rangeClosed(1, 25)
                .mapToObj(i -> new TenantUsageCount("T" + i, i))
                .toList();
        when(capabilityUsageQueryRepository.countCallsByUriPrefixes(anyList(), any(), any(), isNull()))
                .thenReturn(Mono.just(rows));

        StepVerifier.create(capabilityUsageService.queryUsage(null, "workflow", null, null, null, null))
                .assertNext(page -> {
                    assertThat(page.getTotal()).isEqualTo(25L);
                    assertThat(page.getList()).hasSize(20);
                    assertThat(page.getPageSize()).isEqualTo(20);
                    assertThat(page.getHasNext()).isTrue();
                })
                .verifyComplete();

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(capabilityUsageQueryRepository)
                .countCallsByUriPrefixes(anyList(), fromCaptor.capture(), toCaptor.capture(), isNull());
        LocalDate today = LocalDate.now();
        assertThat(fromCaptor.getValue()).isEqualTo(today.minusDays(6).atStartOfDay());
        assertThat(toCaptor.getValue()).isEqualTo(today.atTime(LocalTime.MAX));
        assertThat(ChronoUnit.DAYS.between(
                                fromCaptor.getValue().toLocalDate(),
                                toCaptor.getValue().toLocalDate())
                        + 1)
                .isEqualTo(7L);
    }

    @Test
    @DisplayName("页大小越界时收敛到上限 100")
    void queryUsage_pageSizeClamped() {
        when(capabilityUsageQueryRepository.countCallsByUriPrefixes(anyList(), any(), any(), eq("T001")))
                .thenReturn(Mono.just(List.of(new TenantUsageCount("T001", 1L), new TenantUsageCount("T002", 2L))));

        StepVerifier.create(capabilityUsageService.queryUsage("T001", "user-org", null, null, 1, 500))
                .assertNext(page -> assertThat(page.getPageSize()).isEqualTo(100))
                .verifyComplete();
    }

    @Test
    @DisplayName("租户参数缺省时回退可信租户上下文")
    void queryUsage_tenantFromContext() {
        when(capabilityUsageQueryRepository.countCallsByUriPrefixes(anyList(), any(), any(), eq("CTX1")))
                .thenReturn(Mono.just(List.of(new TenantUsageCount("CTX1", 2L))));

        StepVerifier.create(capabilityUsageService
                        .queryUsage(null, "workflow", null, null, 1, 10)
                        .contextWrite(TenantContext.withTenant("CTX1")))
                .assertNext(page -> {
                    assertThat(page.getTotal()).isEqualTo(1L);
                    assertThat(page.getList().get(0).getTenantId()).isEqualTo("CTX1");
                })
                .verifyComplete();
    }
}
