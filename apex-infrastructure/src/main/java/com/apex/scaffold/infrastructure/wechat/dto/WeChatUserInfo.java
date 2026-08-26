package com.apex.scaffold.infrastructure.wechat.dto;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信用户信息DTO
 *
 * <p>微信用户基本信息
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeChatUserInfo implements Serializable {

    /** 序列化版本 UID */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户唯一标识 */
    private String openId;

    /** 用户统一标识 */
    private String unionId;

    /** 用户昵称 */
    private String nickname;

    /** 性别（1-男，2-女，0-未知） */
    private Integer sex;

    /** 省份 */
    private String province;

    /** 城市 */
    private String city;

    /** 国家 */
    private String country;

    /** 头像URL */
    private String headImgUrl;

    /** 用户特权信息 */
    private String[] privilege;

    /** 语言 */
    private String language;
}
