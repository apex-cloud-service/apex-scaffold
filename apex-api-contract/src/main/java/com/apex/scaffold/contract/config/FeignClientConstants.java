package com.apex.scaffold.contract.config;

/**
 * Feign 客户端常量定义.
 *
 * <p>集中管理服务名称、超时配置等常量，避免硬编码。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public final class FeignClientConstants {

    private FeignClientConstants() {
        // 工具类禁止实例化
    }

    /** 系统管理服务名称（与 Nacos 注册名一致） */
    public static final String SYSTEM_SERVICE_NAME = "apex-system-service";

    /** 认证授权服务名称 */
    public static final String AUTH_SERVICE_NAME = "apex-auth-service";

    /** 工作流服务名称 */
    public static final String WORKFLOW_SERVICE_NAME = "apex-workflow-service";

    /** 办公集成服务名称 */
    public static final String OFFICE_SERVICE_NAME = "apex-office-service";

    /** 文件存储服务名称 */
    public static final String FILE_SERVICE_NAME = "apex-file-service";

    /** 内部 API 路径前缀 */
    public static final String INTERNAL_API_PREFIX = "/internal/v1";
}
