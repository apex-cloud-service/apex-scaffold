package com.apex.scaffold.application.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.application.dto.LoginRequest;
import com.apex.scaffold.domain.enums.RegisterChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 注册来源解析器单元测试。
 *
 * <p>覆盖显式客户端标识优先、登录方式推导、platform 兜底与缺省 UNKNOWN 四类规则。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.3.0
 */
class RegisterChannelResolverTest {

    /** 构造登录请求。 */
    private LoginRequest request(String clientType, String loginType, String platform) {
        LoginRequest request = new LoginRequest();
        request.setClientType(clientType);
        request.setLoginType(loginType);
        request.setPlatform(platform);
        return request;
    }

    @Test
    @DisplayName("显式 clientType=MINI_PROGRAM 优先映射为小程序来源")
    void explicitClientTypeWins() {
        assertThat(RegisterChannelResolver.resolve(request("MINI_PROGRAM", "WX_MINI", null)))
                .isEqualTo(RegisterChannel.MINI_PROGRAM);
        assertThat(RegisterChannelResolver.resolve(request("PC_ADMIN", "PASSWORD", null)))
                .isEqualTo(RegisterChannel.PC_ADMIN);
        assertThat(RegisterChannelResolver.resolve(request("APP_IOS", "WX_SMS", "android")))
                .isEqualTo(RegisterChannel.APP_IOS);
    }

    @Test
    @DisplayName("WX_MINI 登录方式推导为小程序来源")
    void wxMiniLoginTypeMapsToMiniProgram() {
        assertThat(RegisterChannelResolver.resolve(request(null, "WX_MINI", null)))
                .isEqualTo(RegisterChannel.MINI_PROGRAM);
    }

    @Test
    @DisplayName("PASSWORD 登录方式推导为 PC Web 来源")
    void passwordLoginTypeMapsToPcWeb() {
        assertThat(RegisterChannelResolver.resolve(request(null, "PASSWORD", null)))
                .isEqualTo(RegisterChannel.PC_WEB);
    }

    @Test
    @DisplayName("platform 兜底推导 App/H5 来源")
    void platformFallback() {
        assertThat(RegisterChannelResolver.resolve(request(null, "WX_CODE", "android")))
                .isEqualTo(RegisterChannel.APP_ANDROID);
        assertThat(RegisterChannelResolver.resolve(request(null, "WX_CODE", "ios")))
                .isEqualTo(RegisterChannel.APP_IOS);
        assertThat(RegisterChannelResolver.resolve(request(null, "WX_CODE", "harmony")))
                .isEqualTo(RegisterChannel.APP_HARMONY);
        assertThat(RegisterChannelResolver.resolve(request(null, "WX_CODE", "h5")))
                .isEqualTo(RegisterChannel.H5);
    }

    @Test
    @DisplayName("无法识别时返回 UNKNOWN（含空请求与未知平台）")
    void unknownFallback() {
        assertThat(RegisterChannelResolver.resolve(null)).isEqualTo(RegisterChannel.UNKNOWN);
        assertThat(RegisterChannelResolver.resolve(request(null, "WX_CODE", "weird_platform")))
                .isEqualTo(RegisterChannel.UNKNOWN);
        assertThat(RegisterChannelResolver.resolve(request(null, null, null))).isEqualTo(RegisterChannel.UNKNOWN);
    }
}
