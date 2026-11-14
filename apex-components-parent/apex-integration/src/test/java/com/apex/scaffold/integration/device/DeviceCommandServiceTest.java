package com.apex.scaffold.integration.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.device.application.DeviceCommandService;
import com.apex.scaffold.integration.device.domain.Device;
import com.apex.scaffold.integration.device.domain.DeviceCommand;
import com.apex.scaffold.integration.device.domain.repository.DeviceCommandRepository;
import com.apex.scaffold.integration.device.domain.repository.DeviceRepository;
import com.apex.scaffold.integration.device.domain.service.CommandDownlinkPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 命令下行状态机测试（方案 M5 / 批次 E3）。
 *
 * <p>验证：commandId 幂等（重复下发返回既有命令）、下行端口缺失时保持 PENDING、
 * 发布成功迁移 SENT、发布失败保持 PENDING、ACK 迁移与超时兜底。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("命令下行状态机测试")
class DeviceCommandServiceTest {

    private DeviceRepository deviceRepository;

    private DeviceCommandRepository commandRepository;

    @SuppressWarnings("unchecked")
    private final ObjectProvider<CommandDownlinkPort> downlinkPort = mock(ObjectProvider.class);

    private DeviceCommandService service;

    @BeforeEach
    void setUp() {
        deviceRepository = mock(DeviceRepository.class);
        commandRepository = mock(DeviceCommandRepository.class);
        service = new DeviceCommandService(deviceRepository, commandRepository, downlinkPort, new ObjectMapper());

        Device device = new Device();
        device.setTenantId("t1");
        device.setDeviceKey("dev-1");
        when(deviceRepository.findByKey("t1", "dev-1")).thenReturn(Mono.just(device));
        when(commandRepository.insert(any(DeviceCommand.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @Test
    @DisplayName("幂等：重复 commandId 返回既有命令且不再新建")
    void dispatchIsIdempotent() {
        DeviceCommand existing = command("cmd-1", DeviceCommand.STATUS_SENT);
        when(commandRepository.findByCommandId("t1", "cmd-1")).thenReturn(Mono.just(existing));

        StepVerifier.create(service.dispatch("t1", "dev-1", "cmd-1", "{\"a\":1}", null))
                .assertNext(result -> {
                    assertThat(result.duplicate()).isTrue();
                    assertThat(result.command()).isSameAs(existing);
                })
                .verifyComplete();
        verify(deviceRepository, never()).findByKey(anyString(), anyString());
        verify(commandRepository, never()).insert(any(DeviceCommand.class));
    }

    @Test
    @DisplayName("端口缺失：命令以 PENDING 受理，不迁移 SENT")
    void dispatchWithoutDownlinkPortStaysPending() {
        when(downlinkPort.getIfAvailable()).thenReturn(null);
        when(commandRepository.findByCommandId("t1", "cmd-1")).thenReturn(Mono.empty());

        StepVerifier.create(service.dispatch("t1", "dev-1", "cmd-1", "{\"a\":1}", null))
                .assertNext(result -> {
                    assertThat(result.duplicate()).isFalse();
                    assertThat(result.command().getStatus()).isEqualTo(DeviceCommand.STATUS_PENDING);
                    assertThat(result.command().getTimeoutAt()).isNotNull();
                })
                .verifyComplete();
        verify(commandRepository, never()).markSent(anyString(), anyString());
    }

    @Test
    @DisplayName("端口发布成功：PENDING → SENT")
    void dispatchPublishSuccessMovesToSent() {
        CommandDownlinkPort port = mock(CommandDownlinkPort.class);
        when(port.publish(any(DeviceCommand.class))).thenReturn(Mono.just(true));
        when(downlinkPort.getIfAvailable()).thenReturn(port);
        when(commandRepository.findByCommandId("t1", "cmd-1")).thenReturn(Mono.empty());
        when(commandRepository.markSent("t1", "cmd-1")).thenReturn(Mono.just(true));

        StepVerifier.create(service.dispatch("t1", "dev-1", "cmd-1", "{\"a\":1}", 120))
                .assertNext(
                        result -> assertThat(result.command().getCommandId()).isEqualTo("cmd-1"))
                .verifyComplete();
        ArgumentCaptor<DeviceCommand> captor = ArgumentCaptor.forClass(DeviceCommand.class);
        verify(commandRepository).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DeviceCommand.STATUS_PENDING);
        assertThat(captor.getValue().getPayload()).isEqualTo("{\"a\":1}");
        verify(commandRepository).markSent("t1", "cmd-1");
    }

    @Test
    @DisplayName("端口发布失败：命令保持 PENDING（超时扫描兜底）")
    void dispatchPublishFailureKeepsPending() {
        CommandDownlinkPort port = mock(CommandDownlinkPort.class);
        when(port.publish(any(DeviceCommand.class))).thenReturn(Mono.just(false));
        when(downlinkPort.getIfAvailable()).thenReturn(port);
        when(commandRepository.findByCommandId("t1", "cmd-1")).thenReturn(Mono.empty());

        StepVerifier.create(service.dispatch("t1", "dev-1", "cmd-1", "{\"a\":1}", null))
                .assertNext(result -> assertThat(result.command().getStatus()).isEqualTo(DeviceCommand.STATUS_PENDING))
                .verifyComplete();
        verify(commandRepository, never()).markSent(anyString(), anyString());
    }

    @Test
    @DisplayName("非法请求：commandId 缺失或载荷非 JSON 对象被拒绝")
    void dispatchValidatesInput() {
        StepVerifier.create(service.dispatch("t1", "dev-1", " ", "{\"a\":1}", null))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(service.dispatch("t1", "dev-1", "cmd-1", "[1,2]", null))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(service.dispatch("t1", "dev-1", "cmd-1", "not-json", null))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("ACK：归属校验通过后迁移，跨设备命令 404")
    void ackMovesToAcked() {
        DeviceCommand command = command("cmd-1", DeviceCommand.STATUS_SENT);
        command.setDeviceKey("dev-1");
        when(commandRepository.findByCommandId("t1", "cmd-1")).thenReturn(Mono.just(command));
        when(commandRepository.markAcked("t1", "cmd-1")).thenReturn(Mono.just(true));

        StepVerifier.create(service.ack("t1", "dev-1", "cmd-1")).verifyComplete();
        verify(commandRepository).markAcked("t1", "cmd-1");

        StepVerifier.create(service.ack("t1", "dev-2", "cmd-1"))
                .expectErrorSatisfies(error ->
                        assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()))
                .verify();
    }

    @Test
    @DisplayName("超时：条件更新交由仓储执行")
    void timeoutMarksCommand() {
        DeviceCommand command = command("cmd-1", DeviceCommand.STATUS_PENDING);
        command.setTenantId("t1");
        when(commandRepository.markTimeout(command)).thenReturn(Mono.just(true));

        StepVerifier.create(service.timeout(command)).verifyComplete();
        verify(commandRepository).markTimeout(command);
    }

    @Test
    @DisplayName("超时：缺少租户归属的命令拒绝处理")
    void timeoutRejectsCommandWithoutTenant() {
        DeviceCommand command = command("cmd-1", DeviceCommand.STATUS_PENDING);
        command.setTenantId(null);
        StepVerifier.create(service.timeout(command))
                .expectError(IllegalStateException.class)
                .verify();
        verify(commandRepository, never()).markTimeout(any(DeviceCommand.class));
    }

    private DeviceCommand command(String commandId, String status) {
        DeviceCommand command = new DeviceCommand();
        command.setTenantId("t1");
        command.setCommandId(commandId);
        command.setDeviceKey("dev-1");
        command.setStatus(status);
        return command;
    }
}
