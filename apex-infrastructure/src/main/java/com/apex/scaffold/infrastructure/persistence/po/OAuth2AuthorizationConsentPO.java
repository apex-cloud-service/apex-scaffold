package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * OAuth2授权同意持久化对象
 *
 * <p>对应数据库表 {@code oauth2_authorization_consent}。
 * 存储用户对OAuth2客户端的授权同意记录。
 *
 * <h2>表结构映射</h2>
 * <ul>
 *   <li>id - 主键，UUID</li>
 *   <li>registered_client_id - 客户端ID</li>
 *   <li>principal_name - 用户OpenID</li>
 *   <li>authorities - 授权的scope列表(逗号分隔)</li>
 *   <li>created_at - 创建时间</li>
 *   <li>updated_at - 更新时间</li>
 * </ul>
 *
 * <h2>唯一约束</h2>
 * <p>(registered_client_id, principal_name) 组合唯一
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@TableName("oauth2_authorization_consent")
public class OAuth2AuthorizationConsentPO {

    /**
     * 主键ID
     *
     * <p>使用UUID作为主键
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 客户端注册ID
     *
     * <p>关联到 oauth2_registered_client 表
     */
    @TableField("registered_client_id")
    private String registeredClientId;

    /**
     * 用户主体名称 (OpenID)
     *
     * <p>授权的用户唯一标识
     */
    @TableField("principal_name")
    private String principalName;

    /**
     * 授权的权限列表
     *
     * <p>多个scope用逗号分隔，例如: "read,write,user:profile"
     */
    @TableField("authorities")
    private String authorities;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private Instant createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private Instant updatedAt;
}
