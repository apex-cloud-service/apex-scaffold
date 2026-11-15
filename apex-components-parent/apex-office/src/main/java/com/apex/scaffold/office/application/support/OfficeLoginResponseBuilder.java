package com.apex.scaffold.office.application.support;

import com.apex.scaffold.office.application.dto.LoginResponse;
import com.apex.scaffold.office.application.port.OfficeUserSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 办公平台登录响应构建器
 *
 * <p>替代单体的 {@code LoginResponseHelper}，在办公模块内独立构建登录响应对象，消除对
 * 单体应用层的编译期依赖。仅覆盖办公平台 OAuth 登录成功场景（{@code isNewUser=false}、
 * {@code nextAction=AUTHENTICATED}），因此不涉及注册领域事件发布。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>填充 JWT Token 对（accessToken、refreshToken）、Token 类型和过期时间</li>
 *   <li>将 {@link OfficeUserSnapshot} 映射为 {@link LoginResponse.UserInfo}</li>
 *   <li>回填手机号、openId 与 unionId</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.3.0
 */
@Component
public class OfficeLoginResponseBuilder {

    /** Bearer Token 类型标识，符合 RFC 6750 规范 */
    private static final String TOKEN_TYPE = "Bearer";

    /** 办公登录成功后的认证流程动作 */
    private static final String NEXT_ACTION_AUTHENTICATED = "AUTHENTICATED";

    /** AccessToken 过期时间（毫秒），默认 2 小时。expiresIn 由此值 / 1000 计算。 */
    private final long accessTokenExpireMs;

    /**
     * 构造办公平台登录响应构建器。
     *
     * @param accessTokenExpireMs AccessToken 过期时间（毫秒）
     */
    public OfficeLoginResponseBuilder(@Value("${jwt.access-token-expire:7200000}") long accessTokenExpireMs) {
        this.accessTokenExpireMs = accessTokenExpireMs;
    }

    /**
     * 构建办公平台登录响应
     *
     * @param user         登录用户快照
     * @param accessToken  JWT 访问令牌
     * @param refreshToken JWT 刷新令牌
     * @param isNewUser    是否为新注册用户
     * @param phone        用户手机号，可为 null；为 null 时使用用户快照手机号
     * @param clientIp     客户端 IP 地址（保留参数，供未来安全审计消费者使用）
     * @return 包含 JWT Token 对和用户信息的登录响应
     */
    public LoginResponse build(
            OfficeUserSnapshot user,
            String accessToken,
            String refreshToken,
            boolean isNewUser,
            String phone,
            String clientIp) {
        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setTokenType(TOKEN_TYPE);
        response.setExpiresIn(accessTokenExpireMs / 1000L);
        response.setOpenId(user.getOpenId());
        response.setUnionId(user.getUnionId());
        response.setIsNewUser(isNewUser);
        response.setNextAction(NEXT_ACTION_AUTHENTICATED);
        response.setUserInfo(toUserInfo(user, phone));
        return response;
    }

    /**
     * 将用户快照映射为登录响应用户信息
     *
     * @param user  登录用户快照
     * @param phone 显式手机号，为 null 时回退到用户快照手机号
     * @return 用户信息
     */
    private LoginResponse.UserInfo toUserInfo(OfficeUserSnapshot user, String phone) {
        LoginResponse.UserInfo info = new LoginResponse.UserInfo();
        info.setNickName(user.getNickName());
        info.setAvatarUrl(user.getAvatarUrl());
        info.setGender(user.getGender());
        info.setPhone(phone != null ? phone : user.getPhone());
        info.setBirthday(user.getBirthday());
        info.setCountry(user.getCountry());
        info.setProvince(user.getProvince());
        info.setCity(user.getCity());
        return info;
    }
}
