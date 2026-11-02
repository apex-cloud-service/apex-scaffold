package com.apex.scaffold.application.service;

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
 * 能力编码经 {@link CapabilityUsageProperties#getMappings() 映射} 解析为
 * 请求 URI 前缀清单，聚合结果做内存分页（租户维度行数有限）。
 *
 * <h2>口径与约束</h2>
 * <ul>
 *   <li>能力编码必须已配置映射，未知编码返回 {@code PARAM_ERROR}</li>
 *   <li>时间区间缺省为近 7 天（含当天），跨度不超过 {@code max-range-days}（默认 92 天）</li>
 *   <li>租户范围：{@code tenantId} 参数显式指定优先；缺省时回退当前可信租户上下文；
 *       均无（平台管理员视角）时聚合全部租户——访问由 {@code capability:usage:read} 权限门控</li>
 *   <li>只读统计，不做计费与配额扣减</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
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
        List<String> uriPrefixes =
                capability == null ? null : properties.getMappings().get(capability);
        if (uriPrefixes == null || uriPrefixes.isEmpty()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "未知能力编码: " + capability));
        }
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
        return capabilityUsageQueryRepository
                .countCallsByUriPrefixes(uriPrefixes, fromTime, toTime, scopedTenant)
                .map(rows -> paginate(rows, capability, effectivePageNum, effectivePageSize));
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
