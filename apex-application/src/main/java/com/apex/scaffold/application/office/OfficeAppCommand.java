package com.apex.scaffold.application.office;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 办公平台应用配置命令对象
 *
 * <p>用于创建和更新租户级办公平台应用配置的命令参数。
 * 该对象不包含 tenantId 和 platform，这两个字段由服务层从上下文和路径参数获取。
 *
 * <p>字段说明：
 * <ul>
 *   <li>appId：平台分配的应用标识（ClientId/CorpId）</li>
 *   <li>secretReference：密钥环境变量引用名（不保存明文密钥）</li>
 *   <li>redirectUri：OAuth 回调地址</li>
 *   <li>signingSecretReference：签名验证密钥环境变量引用名（可选）</li>
 *   <li>capabilities：平台能力标识，逗号分隔（可选）</li>
 *   <li>enabled：是否启用应用</li>
 *   <li>version：乐观锁版本号（更新时必填）</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfficeAppCommand {
    /** 平台应用 ID */
    private String appId;

    /** 应用密钥环境变量引用名 */
    private String secretReference;

    /** OAuth 回调地址 */
    private String redirectUri;

    /** 签名验证密钥环境变量引用名 */
    private String signingSecretReference;

    /** 平台能力标识（逗号分隔） */
    private String capabilities;

    /** 是否启用 */
    private Boolean enabled;

    /** 乐观锁版本号 */
    private Integer version;
}
