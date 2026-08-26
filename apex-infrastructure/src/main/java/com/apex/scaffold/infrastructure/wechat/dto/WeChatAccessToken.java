package com.apex.scaffold.infrastructure.wechat.dto;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信AccessToken响应DTO
 *
 * <p>微信OAuth2授权返回的Token信息
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeChatAccessToken implements Serializable {

    /** 序列化版本 UID */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 访问令牌 */
    private String accessToken;

    /** 刷新令牌 */
    private String refreshToken;

    /** 用户唯一标识 */
    private String openId;

    /** 用户统一标识 */
    private String unionId;

    /** 过期时间（秒） */
    private Integer expiresIn;

    /** 权限范围 */
    private String scope;
}
