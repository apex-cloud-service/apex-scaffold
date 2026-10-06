package com.apex.scaffold.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 存储条目 —— Redis 中 Token 的 JSON 序列化载体。
 *
 * <p>用于替代过去 Redis 中直接存储纯 JWT 字符串的方式。除了 Token 本体外，
 * 额外携带登录时解析出的用户基础信息，使得鉴权过滤器和 Token 刷新流程
 * 可以从 Redis 直接获取用户信息，减少数据库查询。
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li>{@code token} — JWT Token 字符串（access 或 refresh）</li>
 *   <li>{@code userId} — 用户主键 ID</li>
 *   <li>{@code openId} — 微信 OpenID</li>
 *   <li>{@code nickName} — 用户昵称</li>
 *   <li>{@code avatarUrl} — 头像 URL</li>
 *   <li>{@code gender} — 性别（0-未知/1-男/2-女）</li>
 *   <li>{@code phone} — 手机号</li>
 *   <li>{@code unionId} — 微信 UnionID</li>
 *   <li>{@code sid} — 会话 ID（多会话槽键组成部分，与 JWT claims.sid 对应）</li>
 *   <li>{@code clientType} — 客户端类型（{@code ClientType} 编码）</li>
 *   <li>{@code platform} — 客户端平台（android/ios/h5/harmony/wechat）</li>
 *   <li>{@code loginType} — 登录方式（{@code LoginType} 编码）</li>
 *   <li>{@code deviceId} — 设备标识</li>
 * </ul>
 *
 * <h2>向后兼容</h2>
 * <p>{@code TokenServiceImpl.validateRefreshToken}
 * 会同时兼容旧格式（纯 JWT 字符串）和新格式（JSON 对象），确保平滑过渡。
 * 新增字段均为可空，旧 JSON 反序列化后对应字段为 {@code null}，不影响读取。
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 2.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenStoreEntry {

    /** JWT Token 字符串 */
    private String token;

    /** 用户主键 ID */
    private Long userId;

    /** 微信 OpenID */
    private String openId;

    /** 用户昵称 */
    private String nickName;

    /** 头像 URL */
    private String avatarUrl;

    /** 性别：0-未知、1-男、2-女 */
    private Integer gender;

    /** 手机号 */
    private String phone;

    /** 微信 UnionID */
    private String unionId;

    /** 会话 ID：多会话模型下标识一次登录会话，与 JWT claims.sid 一致 */
    private String sid;

    /** 客户端类型（ClientType 编码，如 PC_ADMIN/MINI_PROGRAM/APP_ANDROID） */
    private String clientType;

    /** 客户端平台（android/ios/h5/harmony/wechat） */
    private String platform;

    /** 登录方式（LoginType 编码，如 WX_CODE/PASSWORD） */
    private String loginType;

    /** 设备标识（客户端上报，可选） */
    private String deviceId;
}
