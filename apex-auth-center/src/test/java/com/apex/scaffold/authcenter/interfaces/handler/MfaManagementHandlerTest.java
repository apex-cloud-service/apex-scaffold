package com.apex.scaffold.authcenter.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.authcenter.domain.model.MfaConfig;
import com.apex.scaffold.authcenter.domain.service.MfaService;
import com.apex.scaffold.authcenter.domain.service.MfaService.BackupCodesResult;
import com.apex.scaffold.authcenter.domain.service.MfaService.MfaActivationResult;
import com.apex.scaffold.authcenter.domain.service.MfaService.TotpSetupInfo;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link MfaManagementHandler} 的单元测试类
 *
 * <p>测试MFA管理Handler的核心逻辑：参数解析、业务调用、响应构建、异常处理。
 *
 * @author Apex Team
 * @since 1.0.0
 */
@DisplayName("MfaManagementHandler 单元测试")
@ExtendWith(MockitoExtension.class)
class MfaManagementHandlerTest {

    @Mock
    private MfaService mfaService;

    private MfaManagementHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MfaManagementHandler(mfaService);
    }

    @Nested
    @DisplayName("enableTotp 方法")
    class EnableTotpTests {

        @Test
        @DisplayName("参数完整时应成功返回TOTP配置信息")
        void shouldEnableTotpSuccessfully() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("userId")).thenReturn(Optional.of("user-1"));
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            TotpSetupInfo info = new TotpSetupInfo("SECRET", "qr-url", "SECRET");
            when(mfaService.enableTotp("user-1", "tenant-1")).thenReturn(Mono.just(info));

            StepVerifier.create(handler.enableTotp(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(mfaService).enableTotp("user-1", "tenant-1");
        }

        @Test
        @DisplayName("缺少userId时应抛出参数校验异常")
        void shouldReturnErrorWhenUserIdMissing() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("userId")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> handler.enableTotp(request));
        }

        @Test
        @DisplayName("service抛出异常时应走handleError返回500")
        void shouldHandleServiceError() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("userId")).thenReturn(Optional.of("user-1"));
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(mfaService.enableTotp("user-1", "tenant-1"))
                    .thenReturn(Mono.error(new IllegalStateException("TOTP已启用")));

            StepVerifier.create(handler.enableTotp(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(500))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("activateTotp 方法")
    class ActivateTotpTests {

        @Test
        @DisplayName("验证码正确时应成功激活并返回备用验证码")
        void shouldActivateTotpSuccessfully() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("userId")).thenReturn(Optional.of("user-1"));
            when(request.queryParam("code")).thenReturn(Optional.of("123456"));
            MfaActivationResult result = new MfaActivationResult(true, new String[] {"CODE1", "CODE2"});
            when(mfaService.verifyAndActivateTotp("user-1", "123456")).thenReturn(Mono.just(result));

            StepVerifier.create(handler.activateTotp(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(mfaService).verifyAndActivateTotp("user-1", "123456");
        }

        @Test
        @DisplayName("缺少code查询参数时应抛出参数校验异常")
        void shouldReturnErrorWhenCodeMissing() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("userId")).thenReturn(Optional.of("user-1"));
            when(request.queryParam("code")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> handler.activateTotp(request));
        }
    }

    @Nested
    @DisplayName("enableSms 方法")
    class EnableSmsTests {

        @Test
        @DisplayName("参数完整时应成功启用短信MFA")
        void shouldEnableSmsSuccessfully() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("userId")).thenReturn(Optional.of("user-1"));
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(request.queryParam("phoneNumber")).thenReturn(Optional.of("+8613800138000"));
            MfaConfig config = MfaConfig.builder()
                    .userId("user-1")
                    .tenantId("tenant-1")
                    .type(MfaConfig.MfaType.SMS)
                    .status(MfaConfig.MfaStatus.PENDING)
                    .build();
            when(mfaService.enableSms("user-1", "tenant-1", "+8613800138000")).thenReturn(Mono.just(config));

            StepVerifier.create(handler.enableSms(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(mfaService).enableSms("user-1", "tenant-1", "+8613800138000");
        }

        @Test
        @DisplayName("缺少phoneNumber时应抛出参数校验异常")
        void shouldReturnErrorWhenPhoneNumberMissing() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("userId")).thenReturn(Optional.of("user-1"));
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(request.queryParam("phoneNumber")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> handler.enableSms(request));
        }
    }

    @Nested
    @DisplayName("enableEmail 方法")
    class EnableEmailTests {

        @Test
        @DisplayName("参数完整时应成功启用邮箱MFA")
        void shouldEnableEmailSuccessfully() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("userId")).thenReturn(Optional.of("user-1"));
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(request.queryParam("email")).thenReturn(Optional.of("user@example.com"));
            MfaConfig config = MfaConfig.builder()
                    .userId("user-1")
                    .tenantId("tenant-1")
                    .type(MfaConfig.MfaType.EMAIL)
                    .status(MfaConfig.MfaStatus.PENDING)
                    .build();
            when(mfaService.enableEmail("user-1", "tenant-1", "user@example.com"))
                    .thenReturn(Mono.just(config));

            StepVerifier.create(handler.enableEmail(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(mfaService).enableEmail("user-1", "tenant-1", "user@example.com");
        }

        @Test
        @DisplayName("邮箱已绑定时应走handleError")
        void shouldHandleServiceError() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("userId")).thenReturn(Optional.of("user-1"));
            when(request.attribute("tenantId")).thenReturn(Optional.of("tenant-1"));
            when(request.queryParam("email")).thenReturn(Optional.of("user@example.com"));
            when(mfaService.enableEmail("user-1", "tenant-1", "user@example.com"))
                    .thenReturn(Mono.error(new IllegalStateException("邮箱已被绑定")));

            StepVerifier.create(handler.enableEmail(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(500))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("disableMfa 方法")
    class DisableMfaTests {

        @Test
        @DisplayName("类型合法时应成功禁用MFA")
        void shouldDisableMfaSuccessfully() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("userId")).thenReturn(Optional.of("user-1"));
            when(request.pathVariable("type")).thenReturn("TOTP");
            when(mfaService.disableMfa("user-1", MfaConfig.MfaType.TOTP)).thenReturn(Mono.empty());

            StepVerifier.create(handler.disableMfa(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(mfaService).disableMfa("user-1", MfaConfig.MfaType.TOTP);
        }

        @Test
        @DisplayName("非法type路径参数应抛出枚举转换异常")
        void shouldReturnErrorWhenTypeInvalid() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("userId")).thenReturn(Optional.of("user-1"));
            when(request.pathVariable("type")).thenReturn("INVALID_TYPE");

            assertThrows(IllegalArgumentException.class, () -> handler.disableMfa(request));
        }
    }

    @Nested
    @DisplayName("verifyMfaCode 方法")
    class VerifyMfaCodeTests {

        @Test
        @DisplayName("验证码正确时应返回true")
        void shouldReturnTrueWhenCodeValid() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("userId")).thenReturn(Optional.of("user-1"));
            when(request.queryParam("code")).thenReturn(Optional.of("123456"));
            when(request.queryParam("type")).thenReturn(Optional.of("TOTP"));
            when(mfaService.verifyMfaCode("user-1", "123456", MfaConfig.MfaType.TOTP))
                    .thenReturn(Mono.just(true));

            StepVerifier.create(handler.verifyMfaCode(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(mfaService).verifyMfaCode("user-1", "123456", MfaConfig.MfaType.TOTP);
        }

        @Test
        @DisplayName("缺少type查询参数应抛出参数校验异常")
        void shouldReturnErrorWhenTypeMissing() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("userId")).thenReturn(Optional.of("user-1"));
            when(request.queryParam("code")).thenReturn(Optional.of("123456"));
            when(request.queryParam("type")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> handler.verifyMfaCode(request));
        }
    }

    @Nested
    @DisplayName("sendMfaCode 方法")
    class SendMfaCodeTests {

        @Test
        @DisplayName("参数完整时应成功发送验证码")
        void shouldSendMfaCodeSuccessfully() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("userId")).thenReturn(Optional.of("user-1"));
            when(request.queryParam("type")).thenReturn(Optional.of("SMS"));
            when(mfaService.sendMfaCode("user-1", MfaConfig.MfaType.SMS)).thenReturn(Mono.empty());

            StepVerifier.create(handler.sendMfaCode(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(mfaService).sendMfaCode("user-1", MfaConfig.MfaType.SMS);
        }

        @Test
        @DisplayName("TOTP类型不支持发送时应走handleError")
        void shouldHandleServiceError() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("userId")).thenReturn(Optional.of("user-1"));
            when(request.queryParam("type")).thenReturn(Optional.of("TOTP"));
            when(mfaService.sendMfaCode("user-1", MfaConfig.MfaType.TOTP))
                    .thenReturn(Mono.error(new IllegalArgumentException("TOTP无需发送验证码")));

            StepVerifier.create(handler.sendMfaCode(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(500))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getUserMfaConfigs 方法")
    class GetUserMfaConfigsTests {

        @Test
        @DisplayName("应返回用户所有MFA配置列表")
        void shouldReturnConfigsSuccessfully() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("userId")).thenReturn(Optional.of("user-1"));
            MfaConfig config = MfaConfig.builder()
                    .userId("user-1")
                    .type(MfaConfig.MfaType.TOTP)
                    .status(MfaConfig.MfaStatus.ACTIVE)
                    .build();
            when(mfaService.getUserMfaConfigs("user-1")).thenReturn(Flux.just(config));

            StepVerifier.create(handler.getUserMfaConfigs(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(mfaService).getUserMfaConfigs("user-1");
        }

        @Test
        @DisplayName("用户无MFA配置时应返回空列表且200")
        void shouldReturnEmptyListWhenNoConfigs() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("userId")).thenReturn(Optional.of("user-1"));
            when(mfaService.getUserMfaConfigs("user-1")).thenReturn(Flux.empty());

            StepVerifier.create(handler.getUserMfaConfigs(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("checkMfaStatus 方法")
    class CheckMfaStatusTests {

        @Test
        @DisplayName("已启用MFA时应返回true")
        void shouldReturnTrueWhenMfaEnabled() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("userId")).thenReturn(Optional.of("user-1"));
            when(mfaService.isMfaEnabled("user-1")).thenReturn(Mono.just(true));

            StepVerifier.create(handler.checkMfaStatus(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(mfaService).isMfaEnabled("user-1");
        }
    }

    @Nested
    @DisplayName("regenerateBackupCodes 方法")
    class RegenerateBackupCodesTests {

        @Test
        @DisplayName("应成功重新生成备用验证码")
        void shouldRegenerateSuccessfully() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("userId")).thenReturn(Optional.of("user-1"));
            BackupCodesResult result = new BackupCodesResult(List.of("A1", "A2").toArray(new String[0]));
            when(mfaService.regenerateBackupCodes("user-1")).thenReturn(Mono.just(result));

            StepVerifier.create(handler.regenerateBackupCodes(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(mfaService).regenerateBackupCodes("user-1");
        }

        @Test
        @DisplayName("用户未启用任何MFA时应走handleError")
        void shouldHandleServiceError() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("userId")).thenReturn(Optional.of("user-1"));
            when(mfaService.regenerateBackupCodes("user-1"))
                    .thenReturn(Mono.error(new IllegalStateException("用户未启用任何MFA")));

            StepVerifier.create(handler.regenerateBackupCodes(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(500))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("verifyBackupCode 方法")
    class VerifyBackupCodeTests {

        @Test
        @DisplayName("备用验证码正确时应返回true")
        void shouldVerifySuccessfully() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("userId")).thenReturn(Optional.of("user-1"));
            when(request.queryParam("code")).thenReturn(Optional.of("ABCD1234"));
            when(mfaService.verifyBackupCode("user-1", "ABCD1234")).thenReturn(Mono.just(true));

            StepVerifier.create(handler.verifyBackupCode(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(mfaService).verifyBackupCode("user-1", "ABCD1234");
        }

        @Test
        @DisplayName("缺少code查询参数应抛出参数校验异常")
        void shouldReturnErrorWhenCodeMissing() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.attribute("userId")).thenReturn(Optional.of("user-1"));
            when(request.queryParam("code")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> handler.verifyBackupCode(request));
        }
    }

    @Nested
    @DisplayName("unlockMfa 方法")
    class UnlockMfaTests {

        @Test
        @DisplayName("管理员参数完整时应成功解锁")
        void shouldUnlockSuccessfully() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.queryParam("userId")).thenReturn(Optional.of("user-1"));
            when(request.queryParam("type")).thenReturn(Optional.of("TOTP"));
            when(request.attribute("userId")).thenReturn(Optional.of("admin-1"));
            when(mfaService.unlockMfa("user-1", MfaConfig.MfaType.TOTP, "admin-1"))
                    .thenReturn(Mono.empty());

            StepVerifier.create(handler.unlockMfa(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            verify(mfaService).unlockMfa(eq("user-1"), eq(MfaConfig.MfaType.TOTP), eq("admin-1"));
        }

        @Test
        @DisplayName("缺少目标userId查询参数应抛出参数校验异常")
        void shouldReturnErrorWhenTargetUserIdMissing() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.queryParam("userId")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> handler.unlockMfa(request));
        }

        @Test
        @DisplayName("目标用户MFA未锁定时应走handleError")
        void shouldHandleServiceError() {
            ServerRequest request = mock(ServerRequest.class);
            when(request.queryParam("userId")).thenReturn(Optional.of("user-1"));
            when(request.queryParam("type")).thenReturn(Optional.of("SMS"));
            when(request.attribute("userId")).thenReturn(Optional.of("admin-1"));
            when(mfaService.unlockMfa("user-1", MfaConfig.MfaType.SMS, "admin-1"))
                    .thenReturn(Mono.error(new IllegalStateException("MFA未锁定")));

            StepVerifier.create(handler.unlockMfa(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(500))
                    .verifyComplete();
        }
    }
}
