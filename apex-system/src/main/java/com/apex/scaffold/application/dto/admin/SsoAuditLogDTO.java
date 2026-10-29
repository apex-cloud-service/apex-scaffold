package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSO 审计日志响应对象
 *
 * <p>用于返回 SSO 单点登录相关的审计日志信息，记录所有 SSO 认证、会话管理等操作的详细信息。
 * 包含操作类型、操作结果、失败原因等，用于安全审计和问题排查。
 *
 * <h2>审计范围</h2>
 * <ul>
 *   <li>SSO 登录：成功/失败的登录尝试</li>
 *   <li>SSO 登出：用户主动登出或会话过期</li>
 *   <li>会话撤销：管理员撤销用户会话</li>
 *   <li>提供商变更：SSO 提供商配置的增删改</li>
 *   <li>连接测试：SSO 连接测试操作</li>
 * </ul>
 *
 * @author Apex Scaffold Generator
 * @since 1.3.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "SSO 审计日志响应对象")
public class SsoAuditLogDTO {

    @Schema(description = "日志 ID", example = "1")
    private Long id;

    @Schema(description = "租户 ID", example = "1")
    private Long tenantId;

    @Schema(description = "SSO 提供商 ID", example = "1")
    private Long providerId;

    @Schema(description = "SSO 提供商名称", example = "企业 SAML 认证")
    private String providerName;

    @Schema(description = "用户 ID（未登录时为空）", example = "100")
    private Long userId;

    @Schema(description = "用户名（未登录时为空）", example = "zhangsan")
    private String username;

    @Schema(
            description = "操作类型（LOGIN-登录/LOGOUT-登出/REVOKE-撤销/TEST_CONNECTION-测试连接/CONFIG_UPDATE-配置更新）",
            example = "LOGIN")
    private String operationType;

    @Schema(description = "操作结果（SUCCESS-成功/FAILED-失败）", example = "SUCCESS")
    private String result;

    @Schema(description = "失败原因（操作失败时记录）", example = "证书验证失败")
    private String failureReason;

    @Schema(description = "操作 IP 地址", example = "192.168.1.100")
    private String operatorIp;

    @Schema(description = "用户代理（浏览器信息）", example = "Mozilla/5.0")
    private String userAgent;

    @Schema(description = "请求参数（JSON 格式，敏感信息已脱敏）", example = "{\"username\": \"zhangsan\"}")
    private String requestParams;

    @Schema(description = "响应结果（JSON 格式，敏感信息已脱敏）", example = "{\"code\": 200}")
    private String responseResult;

    @Schema(description = "执行耗时（毫秒）", example = "150")
    private Long duration;

    @Schema(description = "错误堆栈（发生异常时记录）", example = "java.lang.Exception: Certificate validation failed")
    private String errorStack;

    @Schema(description = "操作描述", example = "用户通过 SAML 协议登录成功")
    private String description;

    @Schema(description = "创建时间", example = "2026-09-19T10:00:00")
    private LocalDateTime createdAt;
}
