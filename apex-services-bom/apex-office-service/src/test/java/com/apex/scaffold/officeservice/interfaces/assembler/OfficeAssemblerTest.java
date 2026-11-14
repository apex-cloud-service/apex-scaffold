package com.apex.scaffold.officeservice.interfaces.assembler;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.office.application.OfficeAppCommand;
import com.apex.scaffold.office.domain.OfficeIdentityBinding;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.domain.OfficePlatformApp;
import com.apex.scaffold.officeservice.interfaces.dto.office.OfficeAppRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.OfficeAppResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.OfficeBindingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

@DisplayName("OfficeAssembler 单元测试")
class OfficeAssemblerTest {

    private OfficeAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = Mappers.getMapper(OfficeAssembler.class);
    }

    @Test
    @DisplayName("toCommand 映射请求字段")
    void toCommand() {
        OfficeAppRequest request = OfficeAppRequest.builder()
                .appId("cli_1")
                .secretReference("SECRET")
                .redirectUri("https://cb")
                .signingSecretReference("SIGN")
                .capabilities("contacts,messages")
                .enabled(true)
                .version(2)
                .build();

        OfficeAppCommand command = assembler.toCommand(request);

        assertThat(command.getAppId()).isEqualTo("cli_1");
        assertThat(command.getSecretReference()).isEqualTo("SECRET");
        assertThat(command.getRedirectUri()).isEqualTo("https://cb");
        assertThat(command.getSigningSecretReference()).isEqualTo("SIGN");
        assertThat(command.getCapabilities()).isEqualTo("contacts,messages");
        assertThat(command.getEnabled()).isTrue();
        assertThat(command.getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("toAppResponse 将平台枚举转为名称字符串")
    void toAppResponse() {
        OfficePlatformApp app = OfficePlatformApp.builder()
                .id(1L)
                .tenantId("tenant-1")
                .platform(OfficePlatform.FEISHU)
                .appId("cli_1")
                .redirectUri("https://cb")
                .capabilities("contacts")
                .enabled(true)
                .version(0)
                .build();

        OfficeAppResponse response = assembler.toAppResponse(app);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTenantId()).isEqualTo("tenant-1");
        assertThat(response.getPlatform()).isEqualTo("FEISHU");
        assertThat(response.getAppId()).isEqualTo("cli_1");
        assertThat(response.getEnabled()).isTrue();
    }

    @Test
    @DisplayName("toBindingResponse 将平台枚举转为名称字符串")
    void toBindingResponse() {
        OfficeIdentityBinding binding = OfficeIdentityBinding.builder()
                .id(2L)
                .tenantId("tenant-1")
                .userId(100L)
                .platform(OfficePlatform.DINGTALK)
                .externalUserId("ext-1")
                .status("APPROVED")
                .enabled(true)
                .version(0)
                .build();

        OfficeBindingResponse response = assembler.toBindingResponse(binding);

        assertThat(response.getId()).isEqualTo(2L);
        assertThat(response.getUserId()).isEqualTo(100L);
        assertThat(response.getPlatform()).isEqualTo("DINGTALK");
        assertThat(response.getExternalUserId()).isEqualTo("ext-1");
        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(response.getEnabled()).isTrue();
    }
}
