package com.apex.scaffold.application.service.auth;

import com.apex.scaffold.application.dto.LoginRequest;
import com.apex.scaffold.common.enums.ClientType;
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
     * <p>解析优先级（批次 2/3 多端改造）：
     * <ol>
     *   <li>显式客户端标识 {@code X-Client-Type} 请求头 / {@code clientType} 请求体字段
     *       （AuthHandler 已将请求头回填到请求体）—— 可直接映射到注册来源</li>
     *   <li>登录方式推导：{@code WX_MINI} → 小程序、{@code PASSWORD} → PC Web</li>
     *   <li>{@code platform} 推导：android/ios/harmony/h5</li>
     *   <li>均无法识别时返回 {@link RegisterChannel#UNKNOWN}，与改造前行为兼容</li>
     * </ol>
     *
     * @param request 登录请求，允许为 {@code null}
     * @return 注册来源；无法识别时返回 {@link RegisterChannel#UNKNOWN}
     */
    public static RegisterChannel resolve(LoginRequest request) {
        if (request == null) {
            return RegisterChannel.UNKNOWN;
        }
        // 1. 显式客户端标识优先（X-Client-Type / clientType）
        RegisterChannel fromClientType = fromClientType(request.getClientType());
        if (fromClientType != null) {
            return fromClientType;
        }
        // 2. 登录方式推导
        String loginType = request.getLoginType();
        if (loginType != null && !loginType.isBlank()) {
            String normalized = loginType.trim().toUpperCase();
            if (LoginType.PASSWORD.name().equals(normalized)) {
                return RegisterChannel.PC_WEB;
            }
            if (LoginType.WX_MINI.name().equals(normalized)) {
                return RegisterChannel.MINI_PROGRAM;
            }
        }
        // 3. platform 推导
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

    /**
     * 客户端类型编码到注册来源的映射。
     *
     * @param clientType 客户端类型编码，可为 {@code null}
     * @return 对应注册来源；UNKNOWN 或无法映射时返回 {@code null}（交由后续规则推导）
     */
    private static RegisterChannel fromClientType(String clientType) {
        ClientType type = ClientType.fromCode(clientType);
        return switch (type) {
            case PC_ADMIN -> RegisterChannel.PC_ADMIN;
            case PC_WEB -> RegisterChannel.PC_WEB;
            case WECHAT_MP -> RegisterChannel.WECHAT_MP;
            case MINI_PROGRAM -> RegisterChannel.MINI_PROGRAM;
            case APP_ANDROID -> RegisterChannel.APP_ANDROID;
            case APP_IOS -> RegisterChannel.APP_IOS;
                // harmony 无 ClientType 常量，由 platform 推导分支兜底（APP_HARMONY）
            case H5 -> RegisterChannel.H5;
            case UNKNOWN -> null;
        };
    }
}
