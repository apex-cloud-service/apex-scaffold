package com.apex.scaffold.application.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth2授权同意请求DTO
 *
 * <p>封装用户对OAuth2客户端的授权同意请求参数。
 *
 * <h2>使用场景</h2>
 * <p>当用户在授权同意页面选择授权或拒绝时，
 * 前端通过此DTO提交用户的决定和授权的scope列表。
 *
 * <h2>示例</h2>
 * <pre>{@code
 * {
 *   "clientId": "apex-admin-web",
 *   "state": "xyz123",
 *   "scopes": ["read", "write", "user:profile"],
 *   "approved": true
 * }
 * }</pre>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2ConsentRequest {

    /**
     * OAuth2客户端ID
     *
     * <p>标识请求授权的第三方应用
     */
    private String clientId;

    /**
     * OAuth2 state参数
     *
     * <p>用于防止CSRF攻击，需要在整个授权流程中保持一致
     */
    private String state;

    /**
     * 请求的权限作用域列表
     *
     * <p>例如: ["read", "write", "user:profile"]
     */
    private List<String> scopes;

    /**
     * 用户是否同意授权
     *
     * <p>true - 用户同意授权<br>
     * false - 用户拒绝授权
     */
    private Boolean approved;
}
