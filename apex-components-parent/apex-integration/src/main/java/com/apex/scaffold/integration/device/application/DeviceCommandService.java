package com.apex.scaffold.integration.device.application;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.device.domain.DeviceCommand;
import com.apex.scaffold.integration.device.domain.DeviceKey;
import com.apex.scaffold.integration.device.domain.repository.DeviceCommandRepository;
import com.apex.scaffold.integration.device.domain.repository.DeviceRepository;
import com.apex.scaffold.integration.device.domain.service.CommandDownlinkPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 命令下行用例（方案 M5 / 批次 E3）。
 *
 * <p>状态机 {@code PENDING → SENT → ACK}，超时由扫描任务置 {@code TIMEOUT}：
 * <ul>
 *   <li>下发幂等：以（租户, commandId）唯一；重复下发返回既有命令且不再推进状态；</li>
 *   <li>PENDING → SENT：经可选 {@link CommandDownlinkPort}（MQTT 桥接）发布成功后迁移；
 *       端口缺失（MQTT 裁剪或未启用）时命令保持 PENDING，由超时扫描兜底；</li>
 *   <li>→ ACK：设备确认（PENDING/SENT 均可直达 ACK，避免确认先于发布回执的乱序丢确认）；</li>
 *   <li>→ TIMEOUT：{@code timeout_at} 已过且仍在 PENDING/SENT。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Service
public class DeviceCommandService {

    private static final int MAX_COMMAND_ID_LENGTH = 64;

    /** 命令默认超时窗口。 */
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    /** 命令超时上限。 */
    static final Duration MAX_TIMEOUT = Duration.ofHours(1);

    private final DeviceRepository deviceRepository;

    private final DeviceCommandRepository commandRepository;

    private final ObjectProvider<CommandDownlinkPort> downlinkPort;

    private final ObjectMapper objectMapper;

    /**
     * 构造命令下行用例。
     *
     * @param deviceRepository  设备仓储
     * @param commandRepository 命令仓储
     * @param downlinkPort      下行发布端口（可选；MQTT 桥接启用时提供）
     * @param objectMapper      Jackson
     */
    public DeviceCommandService(
            DeviceRepository deviceRepository,
            DeviceCommandRepository commandRepository,
            ObjectProvider<CommandDownlinkPort> downlinkPort,
            ObjectMapper objectMapper) {
        this.deviceRepository = deviceRepository;
        this.commandRepository = commandRepository;
        this.downlinkPort = downlinkPort;
        this.objectMapper = objectMapper;
    }

    /**
     * 下发结果。
     *
     * @param command   命令实体（既有或新建）
     * @param duplicate 是否重复下发（命中幂等键）
     */
    public record CommandResult(DeviceCommand command, boolean duplicate) {}

    /**
     * 下发命令（幂等：重复 commandId 返回既有命令）。
     *
     * @param tenantId       租户编码
     * @param deviceKey      设备标识
     * @param commandId      业务命令 ID（调用方提供）
     * @param payloadJson    命令载荷 JSON（对象）
     * @param timeoutSeconds 超时窗口（秒；空取默认 60，上限 3600）
     * @return 下发结果
     */
    public Mono<CommandResult> dispatch(
            String tenantId, String deviceKey, String commandId, String payloadJson, Integer timeoutSeconds) {
        return Mono.defer(() -> {
            DeviceKey key = DeviceKey.of(deviceKey);
            if (commandId == null || commandId.isBlank() || commandId.length() > MAX_COMMAND_ID_LENGTH) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "命令 ID 须为 1~64 位非空白文本"));
            }
            validatePayload(payloadJson);
            return commandRepository
                    .findByCommandId(tenantId, commandId)
                    .map(existing -> new CommandResult(existing, true))
                    .switchIfEmpty(Mono.defer(() -> deviceRepository
                            .findByKey(tenantId, key.value())
                            .switchIfEmpty(
                                    Mono.error(new BusinessException(ResultCode.NOT_FOUND, "设备不存在: " + key.value())))
                            .flatMap(device ->
                                    createAndPublish(tenantId, key.value(), commandId, payloadJson, timeoutSeconds))));
        });
    }

    /**
     * 设备确认命令（PENDING/SENT → ACK）。
     *
     * @param tenantId  租户编码
     * @param deviceKey 设备标识
     * @param commandId 业务命令 ID
     * @throws BusinessException 命令不存在时抛出，错误码为 {@code NOT_FOUND}（HTTP 404）
     */
    public Mono<Void> ack(String tenantId, String deviceKey, String commandId) {
        return commandRepository
                .findByCommandId(tenantId, commandId)
                .filter(command -> Objects.equals(command.getDeviceKey(), deviceKey))
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "命令不存在: " + commandId)))
                .flatMap(command -> commandRepository.markAcked(tenantId, commandId))
                .then();
    }

    /**
     * 将已超时的单条命令置为 TIMEOUT（超时扫描逐条调用；命令须携带租户归属）。
     *
     * @param command 超时命令
     */
    public Mono<Void> timeout(DeviceCommand command) {
        if (command == null
                || command.getTenantId() == null
                || command.getTenantId().isBlank()) {
            return Mono.error(new IllegalStateException("超时命令缺少租户归属"));
        }
        return commandRepository.markTimeout(command).then();
    }

    /** 新建命令（PENDING）→ 经下行端口发布 → 发布成功迁移 SENT。 */
    private Mono<CommandResult> createAndPublish(
            String tenantId, String deviceKey, String commandId, String payloadJson, Integer timeoutSeconds) {
        DeviceCommand command = new DeviceCommand();
        command.setTenantId(tenantId);
        command.setCommandId(commandId);
        command.setDeviceKey(deviceKey);
        command.setPayload(payloadJson);
        command.setStatus(DeviceCommand.STATUS_PENDING);
        command.setTimeoutAt(LocalDateTime.now().plus(resolveTimeout(timeoutSeconds)));
        return commandRepository.insert(command).flatMap(saved -> publishDownlink(saved)
                .thenReturn(new CommandResult(saved, false)));
    }

    /** 下行端口缺失或发布失败均不阻断受理：命令保持 PENDING，由超时扫描兜底。 */
    private Mono<Void> publishDownlink(DeviceCommand command) {
        CommandDownlinkPort port = downlinkPort != null ? downlinkPort.getIfAvailable() : null;
        if (port == null) {
            log.debug("下行端口未启用，命令保持 PENDING: tenant={}, commandId={}", command.getTenantId(), command.getCommandId());
            return Mono.empty();
        }
        return port.publish(command)
                .flatMap(published -> published
                        ? commandRepository
                                .markSent(command.getTenantId(), command.getCommandId())
                                .then()
                        : Mono.<Void>empty())
                .onErrorResume(error -> {
                    log.warn(
                            "命令下行发布失败，保持 PENDING: tenant={}, commandId={}",
                            command.getTenantId(),
                            command.getCommandId(),
                            error);
                    return Mono.empty();
                });
    }

    private Duration resolveTimeout(Integer timeoutSeconds) {
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            return DEFAULT_TIMEOUT;
        }
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        return timeout.compareTo(MAX_TIMEOUT) > 0 ? MAX_TIMEOUT : timeout;
    }

    /** 载荷必须是 JSON 对象。 */
    private void validatePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "命令载荷不能为空");
        }
        try {
            objectMapper.readValue(payloadJson, new TypeReference<java.util.Map<String, Object>>() {});
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "命令载荷 JSON 非法");
        }
    }
}
