package com.apex.scaffold.application.service.auth;

import com.apex.scaffold.application.dto.LoginRequest;
import com.apex.scaffold.domain.enums.LoginType;
import com.apex.scaffold.domain.enums.RegisterChannel;

/**
 * 注册来源解析器。
 *
 * <p>从登录请求推导账户注册来源（{@link RegisterChannel}），在注册票据签发时
 * 打标、注册落库时写入 {@code sys_user.register_channel}。
 *
 * <h2>解析规则</h2>
 * <ul>
 *   <li>账号密码登录 → {@link RegisterChannel#PC_WEB}</li>
 *   <li>其余登录方式按 {@code LoginRequest.platform} 推导：
 *       android/ios/harmony/h5 分别映射到对应渠道</li>
 *   <li>无法识别时返回 {@link RegisterChannel#UNKNOWN}，与改造前行为兼容</li>
 * </ul>
 *
 * <p>批次 2 引入 {@code X-Client-Type} 请求头后，将以显式客户端类型优先，
 * 本解析器降级为兜底推导。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see RegisterChannel
 */
public final class RegisterChannelResolver {

    private RegisterChannelResolver() {}

    /**
     * 从登录请求解析注册来源。
     *
     * @param request 登录请求，允许为 {@code null}
     * @return 注册来源；无法识别时返回 {@link RegisterChannel#UNKNOWN}
     */
    public static RegisterChannel resolve(LoginRequest request) {
        if (request == null) {
            return RegisterChannel.UNKNOWN;
        }
        String loginType = request.getLoginType();
        if (loginType != null && LoginType.PASSWORD.name().equalsIgnoreCase(loginType.trim())) {
            return RegisterChannel.PC_WEB;
        }
        String platform = request.getPlatform();
        if (platform == null || platform.isBlank()) {
            return RegisterChannel.UNKNOWN;
        }
        return switch (platform.trim().toLowerCase()) {
            case "android" -> RegisterChannel.APP_ANDROID;
            case "ios" -> RegisterChannel.APP_IOS;
            case "harmony" -> RegisterChannel.APP_HARMONY;
            case "h5" -> RegisterChannel.H5;
            default -> RegisterChannel.UNKNOWN;
        };
    }
}
