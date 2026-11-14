package com.apex.scaffold.integration.device.application;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.service.DomainEventPublisher;
import com.apex.scaffold.integration.device.domain.DeviceKey;
import com.apex.scaffold.integration.device.domain.DeviceShadowConflictException;
import com.apex.scaffold.integration.device.domain.DeviceTelemetry;
import com.apex.scaffold.integration.device.domain.event.DeviceTelemetryReceived;
import com.apex.scaffold.integration.device.domain.repository.DeviceRepository;
import com.apex.scaffold.integration.device.domain.repository.DeviceShadowRepository;
import com.apex.scaffold.integration.device.domain.repository.DeviceTelemetryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 遥测上行 ingest 用例（方案 M5 / 批次 E3）。
 *
 * <p>受理链：校验设备（租户内存在）→ 解析指标 JSON（仅接受 JSON 对象）→
 * 影子 {@code reported} 更新（version 乐观锁，冲突有限次重读重试）→
 * 逐条发布 {@link DeviceTelemetryReceived} 领域事件（经 DomainEventPublisher）→
 * 批量落库 {@code iot_telemetry}（jdbc 池隔离）。
 *
 * <p>单条 ingest 失败不阻断其余指标；落库失败视为受理失败向上传播
 * （上行通道侧按 broker 语义决定重投或丢弃）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTelemetryService {

    /** 影子乐观锁重试上限（超出后本次 ingest 失败）。 */
    static final int SHADOW_RETRY_LIMIT = 3;

    private final DeviceRepository deviceRepository;

    private final DeviceShadowRepository shadowRepository;

    private final DeviceTelemetryRepository telemetryRepository;

    private final DeviceRuleEngineService ruleEngineService;

    private final DomainEventPublisher eventPublisher;

    private final ObjectMapper objectMapper;

    /**
     * 单条遥测指标。
     *
     * @param metric     遥测指标 JSON（对象）
     * @param recordedAt 上报时间
     */
    public record TelemetryPoint(String metric, LocalDateTime recordedAt) {}

    /**
     * 受理结果。
     *
     * @param acceptedCount 落库指标条数
     * @param shadowVersion 受理后的影子版本
     */
    public record TelemetryAck(int acceptedCount, int shadowVersion) {}

    /**
     * 受理一批遥测指标（批量落库）。
     *
     * @param tenantId  租户编码
     * @param deviceKey 设备标识
     * @param points    指标列表（非空）
     * @return 受理结果
     */
    public Mono<TelemetryAck> ingest(String tenantId, String deviceKey, List<TelemetryPoint> points) {
        return Mono.defer(() -> {
            DeviceKey key = DeviceKey.of(deviceKey);
            if (points == null || points.isEmpty()) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "遥测指标不能为空"));
            }
            for (TelemetryPoint point : points) {
                validateMetric(point);
            }
            return deviceRepository
                    .findByKey(tenantId, key.value())
                    .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "设备不存在: " + key.value())))
                    .flatMap(device -> deviceRepository
                            .touchActivity(tenantId, key.value(), false)
                            .then(updateShadowWithRetry(
                                    tenantId,
                                    key.value(),
                                    points.get(points.size() - 1).metric())))
                    .flatMap(shadow -> publishAndPersist(tenantId, key.value(), points, shadow.getVersion()));
        });
    }

    /** 影子 reported 更新（乐观锁冲突有限次重读重试）。 */
    private Mono<com.apex.scaffold.integration.device.domain.DeviceShadow> updateShadowWithRetry(
            String tenantId, String deviceKey, String reportedJson) {
        return shadowRepository
                .findByKey(tenantId, deviceKey)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "设备影子不存在: " + deviceKey)))
                .flatMap(shadow ->
                        shadowRepository.updateReported(tenantId, deviceKey, reportedJson, shadow.getVersion()))
                .retryWhen(reactor.util.retry.Retry.max(SHADOW_RETRY_LIMIT - 1)
                        .filter(error -> error instanceof DeviceShadowConflictException)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
    }

    /** 发布领域事件（逐条）→ 批量落库 → 规则评估（尽力而为）。 */
    private Mono<TelemetryAck> publishAndPersist(
            String tenantId, String deviceKey, List<TelemetryPoint> points, int shadowVersion) {
        List<DeviceTelemetry> records = points.stream()
                .map(point -> {
                    DeviceTelemetry record = new DeviceTelemetry();
                    record.setTenantId(tenantId);
                    record.setDeviceKey(deviceKey);
                    record.setMetric(point.metric());
                    record.setRecordedAt(point.recordedAt() == null ? LocalDateTime.now() : point.recordedAt());
                    return record;
                })
                .toList();
        return Flux.fromIterable(records)
                .concatMap(record -> eventPublisher.publish(new DeviceTelemetryReceived(
                        tenantId,
                        deviceKey,
                        record.getMetric(),
                        record.getRecordedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))))
                .then(telemetryRepository.insertAll(records))
                .then(evaluateRules(tenantId, deviceKey, points))
                .thenReturn(new TelemetryAck(records.size(), shadowVersion + 1))
                .subscribeOn(BlockingIoSchedulers.event());
    }

    /** 规则评估：失败仅记录告警，不回滚遥测受理。 */
    private Mono<Void> evaluateRules(String tenantId, String deviceKey, List<TelemetryPoint> points) {
        return Flux.fromIterable(points)
                .concatMap(point -> ruleEngineService
                        .evaluate(tenantId, deviceKey, point.metric())
                        .onErrorResume(error -> {
                            log.warn("规则评估失败（跳过）: tenant={}, device={}", tenantId, deviceKey, error);
                            return Mono.empty();
                        }))
                .then();
    }

    /** 指标必须是可解析的 JSON 对象（规则引擎按 Map 取值，标量/数组均不接受）。 */
    private void validateMetric(TelemetryPoint point) {
        if (point == null || point.metric() == null || point.metric().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "遥测指标不能为空");
        }
        try {
            objectMapper.readValue(point.metric(), new TypeReference<java.util.Map<String, Object>>() {});
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "遥测指标 JSON 非法");
        }
    }
}
