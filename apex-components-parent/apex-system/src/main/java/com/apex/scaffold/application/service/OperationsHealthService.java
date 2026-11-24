package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.admin.HealthSnapshotCommand;
import com.apex.scaffold.application.dto.admin.HealthSnapshotDTO;
import com.apex.scaffold.application.dto.admin.HealthTrendDTO;
import com.apex.scaffold.application.dto.admin.TenantHealthSummaryDTO;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.monitor.HealthSnapshotEntity;
import com.apex.scaffold.domain.model.monitor.HealthSnapshotSigner;
import com.apex.scaffold.domain.repository.HealthSnapshotRepository;
import com.apex.scaffold.domain.service.HealthSnapshotSignatureKeyPort;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 运维体检历史应用服务。
 *
 * <p>在即时体检报告之上补齐「历史列表、趋势分析、CSV 导出、租户受限摘要」四段能力，
 * 回答「昨天是否也这样」「风险是否在收敛」。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>快照只保存结论级字段，逐项证据不落库、不导出</li>
 *   <li>所有查询的租户范围由持久化拦截器追加；写快照时租户取自可信上下文</li>
 *   <li>严重度排序 {@code DOWN > DEGRADED > UNKNOWN > UP}，与即时报告一致</li>
 *   <li>导出前先统计规模，超限直接拒绝，避免一次导出拖垮堆内存</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 * @see HealthSnapshotRepository
 */
@Service
@RequiredArgsConstructor
public class OperationsHealthService {

    private static final char CSV_FORMULA_EQUALS = '=';
    private static final char CSV_FORMULA_PLUS = '+';
    private static final char CSV_FORMULA_MINUS = '-';
    private static final char CSV_FORMULA_AT = '@';
    private static final char CSV_TAB = '\t';
    private static final char CSV_CARRIAGE_RETURN = '\r';
    private static final char CSV_DELIMITER = ',';
    private static final char CSV_QUOTE = '"';
    private static final char CSV_LINE_FEED = '\n';

    /** 导出单人单次上限。 */
    static final int EXPORT_MAX_ROWS = 5000;

    /** 趋势取样上限。 */
    static final int TREND_MAX_POINTS = 500;

    /** 租户摘要趋势取样条数。 */
    static final int TENANT_TREND_POINTS = 20;

    /** 趋势默认回看天数。 */
    static final int DEFAULT_TREND_DAYS = 7;

    static final String STATUS_UP = "UP";
    static final String STATUS_DEGRADED = "DEGRADED";
    static final String STATUS_DOWN = "DOWN";
    static final String STATUS_UNKNOWN = "UNKNOWN";

    static final String SCOPE_INSTANCE = "INSTANCE";
    static final String SCOPE_NOTICE = "仅覆盖当前服务实例的进程内采集结果，不代表集群其他实例或其他服务的健康状态";

    static final String CSV_CONTENT_TYPE = "text/csv";

    private static final List<String> ALL_STATUSES = List.of(STATUS_UP, STATUS_DEGRADED, STATUS_UNKNOWN, STATUS_DOWN);

    private final HealthSnapshotRepository snapshotRepository;

    private final HealthSnapshotSignatureKeyPort signatureKeyPort;

    /**
     * 保存一次体检快照。
     *
     * <p>落库前按结论级字段计算 HMAC-SHA256 签名，使读取侧能发现落库后被改写的数据。
     * 密钥缺失时不写签名，读取侧降级为 UNVERIFIABLE，而不是判定篡改。</p>
     *
     * @param command 结论级快照命令
     * @return 已落库的快照视图
     */
    public Mono<HealthSnapshotDTO> capture(HealthSnapshotCommand command) {
        return Mono.deferContextual(context -> {
                    String tenant = context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
                    if (tenant == null || tenant.isBlank()) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "租户上下文不能为空"));
                    }
                    HealthSnapshotEntity entity = toEntity(command, tenant);
                    entity.setSignature(HealthSnapshotSigner.sign(entity, signatureKeyPort.currentSecret()));
                    return snapshotRepository.insert(entity);
                })
                .map(this::toDto);
    }

    /**
     * 分页查询体检快照。
     *
     * @param from 生成时间下界，可为 {@code null}
     * @param to 生成时间上界，可为 {@code null}
     * @param overallStatus 总体结论过滤，可为 {@code null}
     * @param pageNum 页码，从 1 开始
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public Mono<PageResponse<HealthSnapshotDTO>> search(
            LocalDateTime from, LocalDateTime to, String overallStatus, int pageNum, int pageSize) {
        return Mono.defer(() -> snapshotRepository
                .search(from, to, normalizeStatus(overallStatus), pageNum, pageSize)
                .map(page -> PageResponse.of(
                        page.getList().stream().map(this::toDto).toList(),
                        page.getTotal(),
                        page.getPageNum(),
                        page.getPageSize())));
    }

    /**
     * 查询体检趋势。
     *
     * @param from 起始时间，为 {@code null} 时按默认回看天数计算
     * @param to 结束时间，为 {@code null} 时取当前时间
     * @param limit 取样上限，超出 {@link #TREND_MAX_POINTS} 时截断
     * @return 趋势结果
     */
    public Mono<HealthTrendDTO> trend(LocalDateTime from, LocalDateTime to, int limit) {
        return Mono.defer(() -> {
            LocalDateTime end = to != null ? to : LocalDateTime.now();
            LocalDateTime start = from != null ? from : end.minusDays(DEFAULT_TREND_DAYS);
            if (start.isAfter(end)) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "起始时间不能晚于结束时间"));
            }
            int effectiveLimit = limit <= 0 ? TREND_MAX_POINTS : Math.min(limit, TREND_MAX_POINTS);
            return snapshotRepository
                    .findForTrend(start, end, effectiveLimit)
                    .map(points -> buildTrend(start, end, points));
        });
    }

    /**
     * 导出体检快照 CSV。
     *
     * @param from 生成时间下界，可为 {@code null}
     * @param to 生成时间上界，可为 {@code null}
     * @param overallStatus 总体结论过滤，可为 {@code null}
     * @return CSV 文件名与内容
     */
    public Mono<ExportFile> export(LocalDateTime from, LocalDateTime to, String overallStatus) {
        return Mono.defer(() -> {
            String status = normalizeStatus(overallStatus);
            return snapshotRepository.countByRange(from, to).flatMap(total -> {
                if (total != null && total > EXPORT_MAX_ROWS) {
                    return Mono.error(
                            new BusinessException(ResultCode.PARAM_ERROR, "导出记录数超过上限 " + EXPORT_MAX_ROWS + "，请缩小时间范围"));
                }
                // 分页上限与导出上限一致，一页即可覆盖允许导出的最大规模
                return snapshotRepository
                        .search(from, to, status, 1, EXPORT_MAX_ROWS)
                        .map(page -> new ExportFile(
                                exportFileName(),
                                CSV_CONTENT_TYPE,
                                buildCsv(page.getList().stream()
                                                .map(this::toDto)
                                                .toList())
                                        .getBytes(StandardCharsets.UTF_8)));
            });
        });
    }

    /**
     * 查询租户体检摘要。
     *
     * <p>只返回总体结论与业务风险计数，不返回实例标识与逐项证据。
     *
     * @return 租户摘要
     */
    public Mono<TenantHealthSummaryDTO> tenantSummary() {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(DEFAULT_TREND_DAYS);
        return snapshotRepository
                .findForTrend(start, end, TENANT_TREND_POINTS)
                .map(points -> buildTenantSummary(points, end));
    }

    /**
     * 按主键查询单条体检快照。
     *
     * @param id 快照主键
     * @return 快照视图，不存在时为空
     */
    public Mono<HealthSnapshotDTO> findById(Long id) {
        return snapshotRepository.findById(id).map(this::toDto);
    }

    /**
     * 清理保留期之外的体检快照。
     *
     * <p>按批物理删除，直到某批不足 {@code batchSize} 或达到 {@code maxBatches} 为止，
     * 避免单次清理长时间占用数据库连接。
     *
     * @param before 保留截止时间（不含）
     * @param batchSize 单批删除条数
     * @param maxBatches 单次最多执行批次数
     * @return 实际删除总行数
     */
    public Mono<Integer> purge(LocalDateTime before, int batchSize, int maxBatches) {
        return Mono.defer(() -> purgeBatch(before, batchSize, maxBatches, 0));
    }

    private Mono<Integer> purgeBatch(LocalDateTime before, int batchSize, int maxBatches, int purged) {
        if (maxBatches <= 0) {
            return Mono.just(purged);
        }
        return snapshotRepository.purgeGeneratedBefore(before, batchSize).flatMap(deleted -> {
            int total = purged + (deleted == null ? 0 : deleted);
            if (deleted == null || deleted < batchSize) {
                return Mono.just(total);
            }
            return purgeBatch(before, batchSize, maxBatches - 1, total);
        });
    }

    /**
     * 把快照视图渲染为 CSV 文本。
     *
     * <p>供同步导出与异步导出任务共用，保证两条路径产出完全一致的文件格式。
     *
     * @param rows 快照视图
     * @return CSV 文本（含表头）
     */
    public static String renderCsv(List<HealthSnapshotDTO> rows) {
        return buildCsv(rows);
    }

    /**
     * 构造导出文件名。
     *
     * @return 带时间戳的 CSV 文件名
     */
    public static String exportFileName() {
        return "health-snapshots-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".csv";
    }

    /* ==================== 组装 ==================== */

    private HealthTrendDTO buildTrend(LocalDateTime from, LocalDateTime to, List<HealthSnapshotEntity> points) {
        List<HealthTrendDTO.Point> trendPoints = points.stream()
                .map(p -> HealthTrendDTO.Point.builder()
                        .generatedAt(p.getGeneratedAt())
                        .overallStatus(p.getOverallStatus())
                        .upCount(p.getUpCount())
                        .degradedCount(p.getDegradedCount())
                        .downCount(p.getDownCount())
                        .unknownCount(p.getUnknownCount())
                        .notConfiguredCount(p.getNotConfiguredCount())
                        .build())
                .toList();

        Map<String, Integer> counter = new LinkedHashMap<>();
        ALL_STATUSES.forEach(status -> counter.put(status, 0));
        points.forEach(p -> counter.merge(p.getOverallStatus(), 1, Integer::sum));
        List<HealthTrendDTO.StatusCount> distribution = counter.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> HealthTrendDTO.StatusCount.builder()
                        .status(entry.getKey())
                        .count(entry.getValue())
                        .build())
                .toList();

        List<String> ruleVersions = points.stream()
                .map(HealthSnapshotEntity::getRuleVersion)
                .filter(v -> v != null && !v.isBlank())
                .distinct()
                .sorted()
                .toList();

        String startStatus = points.isEmpty() ? null : points.get(0).getOverallStatus();
        String endStatus =
                points.isEmpty() ? null : points.get(points.size() - 1).getOverallStatus();
        Boolean improving = points.size() < 2 ? null : severity(endStatus) < severity(startStatus);

        return HealthTrendDTO.builder()
                .from(from)
                .to(to)
                .sampleCount(points.size())
                .ruleVersions(ruleVersions)
                .distribution(distribution)
                .startStatus(startStatus)
                .endStatus(endStatus)
                .improving(improving)
                .points(trendPoints)
                .build();
    }

    private TenantHealthSummaryDTO buildTenantSummary(List<HealthSnapshotEntity> points, LocalDateTime now) {
        if (points.isEmpty()) {
            return TenantHealthSummaryDTO.builder()
                    .snapshotAt(null)
                    .overallStatus(STATUS_UNKNOWN)
                    .summary("暂无体检快照，无法判断当前状态")
                    .scope(SCOPE_INSTANCE)
                    .scopeNotice(SCOPE_NOTICE)
                    .riskCounts(TenantHealthSummaryDTO.RiskCounts.builder().build())
                    .recentTrend(List.of())
                    .build();
        }
        HealthSnapshotEntity latest = points.get(points.size() - 1);
        // 只暴露业务风险分组的聚合计数，不区分具体检查项，避免平台细节外泄
        TenantHealthSummaryDTO.RiskCounts riskCounts = TenantHealthSummaryDTO.RiskCounts.builder()
                .healthy(latest.getUpCount() == null ? 0 : latest.getUpCount())
                .degraded(latest.getDegradedCount() == null ? 0 : latest.getDegradedCount())
                .down(latest.getDownCount() == null ? 0 : latest.getDownCount())
                .unknown(latest.getUnknownCount() == null ? 0 : latest.getUnknownCount())
                .build();
        List<TenantHealthSummaryDTO.TrendPoint> recentTrend = new ArrayList<>(points.stream()
                .map(p -> TenantHealthSummaryDTO.TrendPoint.builder()
                        .generatedAt(p.getGeneratedAt())
                        .overallStatus(p.getOverallStatus())
                        .build())
                .toList());
        return TenantHealthSummaryDTO.builder()
                .snapshotAt(latest.getGeneratedAt())
                .overallStatus(latest.getOverallStatus())
                .summary(latest.getSummary())
                .scope(SCOPE_INSTANCE)
                .scopeNotice(SCOPE_NOTICE)
                .riskCounts(riskCounts)
                .recentTrend(recentTrend)
                .build();
    }

    private static String buildCsv(List<HealthSnapshotDTO> rows) {
        StringBuilder csv = new StringBuilder();
        csv.append("generatedAt,overallStatus,captureSource,serviceName,instanceId,scope,ruleVersion,")
                .append("up,degraded,down,unknown,notConfigured,summary\n");
        for (HealthSnapshotDTO row : rows) {
            csv.append(csvCell(
                            row.getGeneratedAt() == null
                                    ? null
                                    : row.getGeneratedAt().toString()))
                    .append(',')
                    .append(csvCell(row.getOverallStatus()))
                    .append(',')
                    .append(csvCell(row.getCaptureSource()))
                    .append(',')
                    .append(csvCell(row.getServiceName()))
                    .append(',')
                    .append(csvCell(row.getInstanceId()))
                    .append(',')
                    .append(csvCell(row.getScope()))
                    .append(',')
                    .append(csvCell(row.getRuleVersion()))
                    .append(',')
                    .append(row.getUpCount() == null ? 0 : row.getUpCount())
                    .append(',')
                    .append(row.getDegradedCount() == null ? 0 : row.getDegradedCount())
                    .append(',')
                    .append(row.getDownCount() == null ? 0 : row.getDownCount())
                    .append(',')
                    .append(row.getUnknownCount() == null ? 0 : row.getUnknownCount())
                    .append(',')
                    .append(row.getNotConfiguredCount() == null ? 0 : row.getNotConfiguredCount())
                    .append(',')
                    .append(csvCell(row.getSummary()))
                    .append('\n');
        }
        return csv.toString();
    }

    /**
     * CSV 单元格转义。
     *
     * <p>以 {@code = + - @} 开头的文本会被表格软件当作公式执行，前置单引号阻断；
     * 逗号、引号与换行按 RFC 4180 加引号转义。
     */
    private static String csvCell(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String value = raw;
        char first = value.charAt(0);
        if (first == CSV_FORMULA_EQUALS
                || first == CSV_FORMULA_PLUS
                || first == CSV_FORMULA_MINUS
                || first == CSV_FORMULA_AT
                || first == CSV_TAB
                || first == CSV_CARRIAGE_RETURN) {
            value = "'" + value;
        }
        boolean containsDelimiter = value.indexOf(CSV_DELIMITER) >= 0;
        boolean containsQuote = value.indexOf(CSV_QUOTE) >= 0;
        boolean containsLineFeed = value.indexOf(CSV_LINE_FEED) >= 0;
        if (containsDelimiter || containsQuote || containsLineFeed) {
            value = CSV_QUOTE + value.replace("\"", "\"\"") + CSV_QUOTE;
        }
        return value;
    }

    private HealthSnapshotDTO toDto(HealthSnapshotEntity entity) {
        return toDto(entity, signatureKeyPort.currentSecret());
    }

    /**
     * 把快照实体转换为视图，并按给定密钥校验完整性。
     *
     * <p>异步导出任务在无请求上下文的后台线程执行，密钥由调用方一次性取出传入，
     * 避免逐条重复解析配置。</p>
     *
     * @param entity 快照实体
     * @param secret 签名密钥，为空时完整性为 UNVERIFIABLE
     * @return 快照视图
     */
    public static HealthSnapshotDTO toDto(HealthSnapshotEntity entity, String secret) {
        return HealthSnapshotDTO.builder()
                .id(entity.getId())
                .captureSource(entity.getCaptureSource())
                .serviceName(entity.getServiceName())
                .instanceId(entity.getInstanceId())
                .scope(entity.getScope())
                .ruleVersion(entity.getRuleVersion())
                .overallStatus(entity.getOverallStatus())
                .summary(entity.getSummary())
                .integrity(HealthSnapshotSigner.verify(entity, secret))
                .upCount(entity.getUpCount())
                .degradedCount(entity.getDegradedCount())
                .downCount(entity.getDownCount())
                .unknownCount(entity.getUnknownCount())
                .notConfiguredCount(entity.getNotConfiguredCount())
                .generatedAt(entity.getGeneratedAt())
                .createTime(entity.getCreateTime())
                .build();
    }

    private HealthSnapshotEntity toEntity(HealthSnapshotCommand command, String tenant) {
        HealthSnapshotEntity entity = new HealthSnapshotEntity();
        entity.setCaptureSource(
                command.getCaptureSource() == null
                        ? HealthSnapshotEntity.CAPTURE_SOURCE_MANUAL
                        : command.getCaptureSource());
        entity.setServiceName(command.getServiceName());
        entity.setInstanceId(command.getInstanceId());
        entity.setScope(command.getScope() == null ? SCOPE_INSTANCE : command.getScope());
        entity.setRuleVersion(command.getRuleVersion());
        entity.setOverallStatus(command.getOverallStatus());
        entity.setSummary(command.getSummary());
        entity.setUpCount(command.getUpCount());
        entity.setDegradedCount(command.getDegradedCount());
        entity.setDownCount(command.getDownCount());
        entity.setUnknownCount(command.getUnknownCount());
        entity.setNotConfiguredCount(command.getNotConfiguredCount());
        entity.setGeneratedAt(command.getGeneratedAt() == null ? LocalDateTime.now() : command.getGeneratedAt());
        entity.setTenantId(tenant);
        return entity;
    }

    /**
     * 归一化总体结论取值。
     *
     * <p>供异步导出任务在提交阶段校验过滤条件，与同步导出保持同一套白名单。</p>
     *
     * @param overallStatus 原始取值，可为 {@code null}
     * @return 大写归一化结果，空值返回 {@code null}
     */
    public static String normalizeStatus(String overallStatus) {
        if (overallStatus == null || overallStatus.isBlank()) {
            return null;
        }
        String normalized = overallStatus.trim().toUpperCase();
        if (!ALL_STATUSES.contains(normalized)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "总体结论取值非法: " + overallStatus);
        }
        return normalized;
    }

    private static int severity(String status) {
        if (status == null) {
            return 0;
        }
        return switch (status) {
            case STATUS_DOWN -> 5;
            case STATUS_DEGRADED -> 4;
            case STATUS_UNKNOWN -> 3;
            case STATUS_UP -> 2;
            default -> 1;
        };
    }

    /**
     * 导出文件载体。
     *
     * @param fileName 文件名
     * @param contentType 内容类型
     * @param content 文件内容
     */
    public record ExportFile(String fileName, String contentType, byte[] content) {}
}
