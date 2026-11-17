package com.apex.scaffold.application.service;

import com.apex.scaffold.application.capability.CapabilityCatalog;
import com.apex.scaffold.application.config.CapabilityUsageProperties;
import com.apex.scaffold.application.dto.usage.CapabilityUsageDTO;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.repository.CapabilityUsageQueryRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 能力调用用量查询服务（方案 D4 计量，只读）。
 *
 * <p>基于既有操作审计日志按 租户 × 能力 输出接口调用次数：
 * 能力编码经 {@code CapabilityUsageProperties#getMappings()} 映射解析为
 * 请求 URI 前缀清单，聚合结果做内存分页（租户维度行数有限）。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>能力编码必须已配置映射，未知编码返回 {@code PARAM_ERROR}</li>
 *   <li>时间区间缺省为近 7 天（含当天），跨度不超过 {@code max-range-days}（默认 92 天）</li>
 *   <li>租户范围：{@code tenantId} 参数显式指定优先；缺省时回退当前可信租户上下文；
 *       均无（平台管理员视角）时聚合全部租户——访问由 {@code capability:usage:read} 权限门控</li>
 *   <li>只读统计，不做计费与配额扣减</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Service
@RequiredArgsConstructor
public class CapabilityUsageService {

    /** 缺省统计天数（近 7 天，含当天）。 */
    private static final int DEFAULT_RANGE_DAYS = 7;

    /** 缺省页大小。 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 页大小上限。 */
    private static final int MAX_PAGE_SIZE = 100;

    private final CapabilityUsageQueryRepository capabilityUsageQueryRepository;

    private final CapabilityUsageProperties properties;

    private final CapabilityMetricClient capabilityMetricClient;

    /**
     * 分页查询能力调用用量。
     *
     * @param tenantId  租户标识（可空：回退租户上下文，均无则为平台全量视角）
     * @param capability 能力编码（必填，须已配置 URI 前缀映射）
     * @param from      起始日期（含，可空：默认近 7 天）
     * @param to        结束日期（含，可空：默认当天）
     * @param pageNum   页码（可空：默认 1）
     * @param pageSize  页大小（可空：默认 20，上限 100）
     * @return 用量分页结果
     */
    public Mono<PageResponse<CapabilityUsageDTO>> queryUsage(
            String tenantId, String capability, LocalDate from, LocalDate to, Integer pageNum, Integer pageSize) {
        return Mono.deferContextual(context -> {
            String scopedTenant = (tenantId != null && !tenantId.isBlank())
                    ? tenantId.trim()
                    : context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
            return doQuery(scopedTenant, capability, from, to, pageNum, pageSize);
        });
    }

    /**
     * 执行校验与聚合查询。
     *
     * @param scopedTenant 解析后的租户范围（可空 = 全部租户）
     * @param capability   能力编码
     * @param from         起始日期（可空）
     * @param to           结束日期（可空）
     * @param pageNum      页码（可空）
     * @param pageSize     页大小（可空）
     * @return 用量分页结果
     */
    private Mono<PageResponse<CapabilityUsageDTO>> doQuery(
            String scopedTenant, String capability, LocalDate from, LocalDate to, Integer pageNum, Integer pageSize) {
        if (CapabilityCatalog.find(capability) == null) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "未知能力编码: " + capability));
        }
        List<String> uriPrefixes = properties.getMappings().getOrDefault(capability, List.of());
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(DEFAULT_RANGE_DAYS - 1L);
        if (effectiveFrom.isAfter(effectiveTo)) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "from 不能晚于 to"));
        }
        if (ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) + 1 > properties.getMaxRangeDays()) {
            return Mono.error(
                    new BusinessException(ResultCode.PARAM_ERROR, "查询范围超过上限 " + properties.getMaxRangeDays() + " 天"));
        }
        int effectivePageNum = pageNum != null && pageNum > 0 ? pageNum : 1;
        int effectivePageSize = pageSize != null ? Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        LocalDateTime fromTime = effectiveFrom.atStartOfDay();
        LocalDateTime toTime = effectiveTo.atTime(LocalTime.MAX);
        Mono<PageResponse<CapabilityUsageDTO>> pageMono = uriPrefixes.isEmpty()
                ? Mono.just(emptyUsage(scopedTenant, capability, effectivePageNum, effectivePageSize))
                : capabilityUsageQueryRepository
                        .countCallsByUriPrefixes(uriPrefixes, fromTime, toTime, scopedTenant)
                        .map(rows -> paginate(rows, capability, effectivePageNum, effectivePageSize));
        if (scopedTenant == null || scopedTenant.isBlank()) {
            // 平台全量视角：存量维度按租户逐个拉取代价过高，仅返回调用次数
            return pageMono;
        }
        return pageMono.flatMap(page -> attachStockMetric(page, capability, scopedTenant));
    }

    /**
     * 为单租户查询补充存量维度（D4 四维度计量）。
     *
     * <p>存量指标（存储用量/设备数/消息量）来自对应域内部统计端点；按源降级——
     * 指标不可得时保持 {@code null}，不影响调用次数维度。
     *
     * @param page       调用次数分页结果
     * @param capability 能力编码
     * @param tenantId   租户标识（单租户查询）
     * @return 补充存量维度后的分页结果
     */
    private Mono<PageResponse<CapabilityUsageDTO>> attachStockMetric(
            PageResponse<CapabilityUsageDTO> page, String capability, String tenantId) {
        CapabilityUsageProperties.MetricSource source = properties.getMetrics().get(capability);
        if (source == null) {
            return Mono.just(page);
        }
        return capabilityMetricClient
                .fetch(capability, tenantId)
                .map(value -> {
                    page.getList().forEach(row -> applyMetric(row, source.field(), value));
                    return page;
                })
                .defaultIfEmpty(page);
    }

    /**
     * 按存量指标字段名写入 DTO 对应维度。
     *
     * @param row    用量行
     * @param field  指标字段名（{@code CapabilityUsageProperties.MetricSource#field}）
     * @param value  指标数值
     */
    private void applyMetric(CapabilityUsageDTO row, String field, long value) {
        switch (field) {
            case "storageUsageBytes" -> row.setStorageUsageBytes(value);
            case "deviceCount" -> row.setDeviceCount(value);
            case "messageCount" -> row.setMessageCount(value);
            default -> {
                // 未知指标字段：忽略（配置错误由配置评审拦截，不影响用量查询）
            }
        }
    }

    /**
     * 单租户查询无调用统计时生成零调用行，保留存量维度展示。
     *
     * @param tenantId 租户标识（可空表示平台全量视角）
     * @param capability 能力编码
     * @param pageNum 页码
     * @param pageSize 页大小
     * @return 单租户为零调用一页；平台视角为空页
     */
    private PageResponse<CapabilityUsageDTO> emptyUsage(String tenantId, String capability, int pageNum, int pageSize) {
        if (tenantId == null || tenantId.isBlank()) {
            return PageResponse.of(List.of(), 0L, pageNum, pageSize);
        }
        return PageResponse.of(List.of(new CapabilityUsageDTO(tenantId, capability, 0L)), 1L, pageNum, pageSize);
    }

    /**
     * 将聚合行映射为 DTO 并做内存分页。
     *
     * @param rows     聚合行（租户标识升序）
     * @param capability 能力编码
     * @param pageNum  页码
     * @param pageSize 页大小
     * @return 分页结果
     */
    private PageResponse<CapabilityUsageDTO> paginate(
            List<CapabilityUsageQueryRepository.TenantUsageCount> rows, String capability, int pageNum, int pageSize) {
        List<CapabilityUsageDTO> all = rows.stream()
                .map(row -> new CapabilityUsageDTO(row.tenantId(), capability, row.callCount()))
                .toList();
        int total = all.size();
        int fromIndex = Math.min((pageNum - 1) * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        return PageResponse.of(all.subList(fromIndex, toIndex), (long) total, pageNum, pageSize);
    }
}
