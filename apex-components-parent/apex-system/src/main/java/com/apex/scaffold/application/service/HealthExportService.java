package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.admin.HealthExportTaskDTO;
import com.apex.scaffold.application.dto.admin.HealthSnapshotDTO;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.monitor.HealthExportTaskEntity;
import com.apex.scaffold.domain.model.monitor.HealthSnapshotEntity;
import com.apex.scaffold.domain.repository.HealthExportTaskRepository;
import com.apex.scaffold.domain.repository.HealthSnapshotRepository;
import com.apex.scaffold.domain.service.HealthExportSettingsPort;
import com.apex.scaffold.domain.service.HealthSnapshotSignatureKeyPort;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 体检快照异步导出应用服务。
 *
 * <p>当导出记录数超过同步上限时，改为提交后台任务：提交侧只落一条 {@code PENDING} 任务，
 * 由 {@code HealthExportTaskScheduler} 认领并生成 CSV；前端轮询任务状态，完成后经下载端点取文件。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>提交时立即校验过滤条件与规模，超限直接拒绝，不产生注定失败的任务</li>
 *   <li>任务与文件同属租户数据，查询与下载均由持久化拦截器追加租户范围</li>
 *   <li>结果文件在保留期后由清理任务删除，避免大字段长期占用存储</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 * @see HealthExportTaskRepository
 */
@Service
@RequiredArgsConstructor
public class HealthExportService {

    /** 失败原因落库前的最大长度。 */
    static final int MAX_ERROR_LENGTH = 500;

    private final HealthExportTaskRepository taskRepository;

    private final HealthSnapshotRepository snapshotRepository;

    private final HealthExportSettingsPort settings;

    private final HealthSnapshotSignatureKeyPort signatureKeyPort;

    /**
     * 提交异步导出任务。
     *
     * @param from 生成时间下界，可为 {@code null}
     * @param to 生成时间上界，可为 {@code null}
     * @param overallStatus 总体结论过滤，可为 {@code null}
     * @return 已落库的任务视图
     */
    public Mono<HealthExportTaskDTO> submit(LocalDateTime from, LocalDateTime to, String overallStatus) {
        return Mono.deferContextual(context -> {
                    String tenant = context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
                    if (tenant == null || tenant.isBlank()) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "租户上下文不能为空"));
                    }
                    String status;
                    try {
                        status = OperationsHealthService.normalizeStatus(overallStatus);
                    } catch (BusinessException e) {
                        return Mono.error(e);
                    }
                    if (from != null && to != null && from.isAfter(to)) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "起始时间不能晚于结束时间"));
                    }
                    int maxRows = settings.maxRows();
                    return snapshotRepository.countByRange(from, to).flatMap(total -> {
                        if (total != null && total > maxRows) {
                            return Mono.error(
                                    new BusinessException(ResultCode.PARAM_ERROR, "导出记录数超过上限 " + maxRows + "，请缩小时间范围"));
                        }
                        return taskRepository.insert(newPendingTask(tenant, from, to, status));
                    });
                })
                .map(HealthExportService::toDto);
    }

    /**
     * 查询导出任务状态。
     *
     * @param id 任务主键
     * @return 任务视图；不存在或跨租户时为空
     */
    public Mono<HealthExportTaskDTO> findTask(Long id) {
        return taskRepository.findById(id).map(HealthExportService::toDto);
    }

    /**
     * 下载已完成任务的结果文件。
     *
     * @param id 任务主键
     * @return 结果文件；任务不存在、未完成或跨租户时为空
     */
    public Mono<ExportResult> findDownload(Long id) {
        return taskRepository.findById(id).flatMap(task -> {
            byte[] content = task.getContent();
            if (content == null || content.length == 0) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "导出任务尚未完成，暂不可下载"));
            }
            return Mono.just(new ExportResult(
                    task.getFileName() == null ? OperationsHealthService.exportFileName() : task.getFileName(),
                    OperationsHealthService.CSV_CONTENT_TYPE,
                    content));
        });
    }

    /**
     * 执行一次导出并生成 CSV 文件。
     *
     * <p>供后台调度器调用；调用方负责提供租户上下文、认领任务并回写状态。</p>
     *
     * @param task 已认领的任务
     * @return CSV 文件名、记录数与内容
     */
    public Mono<GeneratedFile> generate(HealthExportTaskEntity task) {
        return Mono.defer(() -> {
            int maxRows = settings.maxRows();
            return snapshotRepository
                    .countByRange(task.getQueryFrom(), task.getQueryTo())
                    .flatMap(total -> {
                        if (total != null && total > maxRows) {
                            return Mono.error(
                                    new BusinessException(ResultCode.PARAM_ERROR, "导出记录数超过上限 " + maxRows + "，请缩小时间范围"));
                        }
                        return snapshotRepository
                                .search(task.getQueryFrom(), task.getQueryTo(), task.getQueryStatus(), 1, maxRows)
                                .map(page -> {
                                    String secret = signatureKeyPort.currentSecret();
                                    List<HealthSnapshotDTO> rows = page.getList().stream()
                                            .map(entity -> toSnapshotDto(entity, secret))
                                            .toList();
                                    String csv = OperationsHealthService.renderCsv(rows);
                                    return new GeneratedFile(
                                            OperationsHealthService.exportFileName(),
                                            rows.size(),
                                            csv.getBytes(StandardCharsets.UTF_8));
                                });
                    });
        });
    }

    /**
     * 清理超期保留的导出结果。
     *
     * @param tenantId 租户标识
     * @param limit 单次删除条数
     * @return 实际删除的总条数，循环清理直至某轮不足 {@code limit}
     */
    public Mono<Integer> purgeExpired(String tenantId, int limit) {
        LocalDateTime before = settings.resultExpireBefore(LocalDateTime.now());
        return purgeExpiredBatch(tenantId, before, limit, 0);
    }

    private Mono<Integer> purgeExpiredBatch(String tenantId, LocalDateTime before, int limit, int purged) {
        return taskRepository.purgeCompletedBefore(tenantId, before, limit).flatMap(deleted -> {
            int total = purged + (deleted == null ? 0 : deleted);
            if (deleted == null || deleted < limit) {
                return Mono.just(total);
            }
            return purgeExpiredBatch(tenantId, before, limit, total);
        });
    }

    private HealthExportTaskEntity newPendingTask(String tenant, LocalDateTime from, LocalDateTime to, String status) {
        HealthExportTaskEntity task = new HealthExportTaskEntity();
        task.setQueryFrom(from);
        task.setQueryTo(to);
        task.setQueryStatus(status);
        task.setStatus(HealthExportTaskEntity.STATUS_PENDING);
        task.setAttempts(0);
        task.setTenantId(tenant);
        return task;
    }

    private static HealthExportTaskDTO toDto(HealthExportTaskEntity entity) {
        if (entity == null) {
            return null;
        }
        return HealthExportTaskDTO.builder()
                .id(entity.getId())
                .queryFrom(entity.getQueryFrom())
                .queryTo(entity.getQueryTo())
                .queryStatus(entity.getQueryStatus())
                .status(entity.getStatus())
                .attempts(entity.getAttempts())
                .lastError(entity.getLastError())
                .fileName(entity.getFileName())
                .rowCount(entity.getRowCount())
                .completedAt(entity.getCompletedAt())
                .createTime(entity.getCreateTime())
                .build();
    }

    private static HealthSnapshotDTO toSnapshotDto(HealthSnapshotEntity entity, String secret) {
        return OperationsHealthService.toDto(entity, secret);
    }

    /**
     * 生成结果。
     *
     * @param fileName 文件名
     * @param rowCount 记录数
     * @param content CSV 内容
     */
    public record GeneratedFile(String fileName, int rowCount, byte[] content) {}

    /**
     * 下载结果。
     *
     * @param fileName 文件名
     * @param contentType 内容类型
     * @param content 文件内容
     */
    public record ExportResult(String fileName, String contentType, byte[] content) {}
}
