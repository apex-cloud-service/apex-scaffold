package com.apex.scaffold.common.constant;

import com.apex.scaffold.common.enums.Gender;
import com.apex.scaffold.common.enums.UserStatus;
import com.apex.scaffold.common.result.ResultCode;
import java.util.List;

/**
 * 系统常量类
 *
 * <p>定义系统中使用的常量。部分常量引用枚举的规范值以避免重复定义，
 * 确保系统中同一概念的取值来源唯一。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><b>性别常量</b>：引用 {@link Gender} 枚举的 {@code getCode()} 值</li>
 *   <li><b>用户状态常量</b>：引用 {@link UserStatus} 枚举的 {@code getCode()} 值</li>
 *   <li><b>错误码常量</b>：引用 {@link ResultCode} 枚举的 {@code getCode()} 值</li>
 *   <li><b>分页常量</b>：独立定义，无对应枚举（仅 {@link com.apex.scaffold.common.dto.PageRequest} 中有默认值）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SystemConstants {

    /** 私有构造函数，防止实例化 */
    private SystemConstants() {
        throw new UnsupportedOperationException("常量类不能实例化");
    }

    /** 状态：正常（引用 UserStatus.NORMAL 的 code 值） */
    public static final int STATUS_NORMAL = UserStatus.NORMAL.getCode();

    /** 状态：禁用（引用 UserStatus.DISABLED 的 code 值） */
    public static final int STATUS_DISABLED = UserStatus.DISABLED.getCode();

    /** 删除标记：未删除 */
    public static final int NOT_DELETED = 0;

    /** 删除标记：已删除 */
    public static final int DELETED = 1;

    /** 性别：未知（引用 Gender.UNKNOWN 的 code 值） */
    public static final int GENDER_UNKNOWN = Gender.UNKNOWN.getCode();

    /** 性别：男（引用 Gender.MALE 的 code 值） */
    public static final int GENDER_MALE = Gender.MALE.getCode();

    /** 性别：女（引用 Gender.FEMALE 的 code 值） */
    public static final int GENDER_FEMALE = Gender.FEMALE.getCode();

    /** 操作日志状态：成功 */
    public static final int LOG_STATUS_SUCCESS = 0;

    /** 操作日志状态：失败 */
    public static final int LOG_STATUS_FAIL = 1;

    /** 默认页码（独立常量，对应 PageRequest.pageNum 默认值） */
    public static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页大小（独立常量，对应 PageRequest.pageSize 默认值） */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 最大每页大小（独立常量，对应 PageRequest 的 Max 校验值） */
    public static final int MAX_PAGE_SIZE = 100;

    /** Token前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** Token请求头名称 */
    public static final String TOKEN_HEADER = "Authorization";

    /** 用户ID请求属性 */
    public static final String USER_ID_ATTR = "userId";

    /** 用户名请求属性 */
    public static final String USER_NAME_ATTR = "userName";

    /** OpenID请求属性 */
    public static final String OPEN_ID_ATTR = "openId";

    /** 追踪ID请求头 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** 布尔值：真（数据库存储） */
    public static final int BOOLEAN_TRUE = 1;

    /** 布尔值：假（数据库存储） */
    public static final int BOOLEAN_FALSE = 0;

    /** 限流错误码（引用 ResultCode.RATE_LIMIT 的 code 值） */
    public static final int ERROR_CODE_RATE_LIMIT = ResultCode.RATE_LIMIT.getCode();

    /** 服务不可用错误码（引用 ResultCode.CIRCUIT_BREAKER 的 code 值） */
    public static final int ERROR_CODE_SERVICE_UNAVAILABLE = ResultCode.CIRCUIT_BREAKER.getCode();

    /** 降级错误码（引用 ResultCode.DEGRADE 的 code 值） */
    public static final int ERROR_CODE_DEGRADE = ResultCode.DEGRADE.getCode();

    /** 同频解锁所需最低信号数 */
    public static final int SAME_FREQUENCY_UNLOCK_MIN_SIGNALS = 10;

    /**
     * 与管理员通配路径重叠、但只要求普通登录态的本人查询接口。
     *
     * <p>角色和权限管理接口使用 {@code /roles/**}、{@code /permissions/**} 保护，
     * 本人查询路径也会命中这些模式，因此必须在管理员规则之前显式声明为已认证可访问。
     */
    public static final List<String> AUTHENTICATED_USER_PATHS =
            List.of("/api/v1/auth/roles/my", "/api/v1/auth/permissions/my");

    /**
     * 组织目录内部接口路径（集群内直连，不经网关）
     *
     * <p>供其他服务（如 office 组织同步）按手机号、邮箱或登录名解析本租户真实用户与部门，
     * 调用方不携带 JWT。租户必须由 {@code X-Tenant-Id} 请求头显式声明，并在 Handler 内
     * 通过 {@code TenantContext.withTenant(...)} 绑定到 Reactor Context，
     * 不依赖 {@code @TenantAware}（该切面要求已认证主体）。
     */
    public static final List<String> INTERNAL_ORG_PATHS = List.of("/internal/v1/org/**");

    /** 认证期租户状态查询内部路径（统一认证收口 A6）：集群内服务直连，不经网关 */
    public static final List<String> INTERNAL_TENANT_PATHS = List.of("/internal/v1/tenants/**");

    /** 跨域只读聚合门面路径（方案 D5）：集群内服务直连，不经网关，租户范围由 tenantId 参数显式声明 */
    public static final List<String> INTERNAL_OVERVIEW_PATHS = List.of("/internal/v1/overview");

    /**
     * 公开路径定义（无需认证）
     *
     * <p>定义所有不需要 JWT 认证即可访问的接口路径，用于 SecurityConfig
     * 和 JwtAuthenticationFilter 中的权限豁免判断。所有路径在
     * SecurityConfig.securityWebFilterChain() 中通过
     * pathMatchers(...).permitAll() 配置。
     *
     * <h2>设计说明</h2>
     * <ul>
     *   <li>登录、Token 刷新、短信验证码和本机号校验 — 建立登录态前必须调用的认证接口</li>
     *   <li>/api/v1/onboarding/policies — 登录前可查阅的协议与隐私政策</li>
     *   <li>/api/v1/health — 应用业务健康检查</li>
     *   <li>/health — 基础健康检查端点</li>
     *   <li>/ — 根路径探活（负载均衡/反向代理默认探活路径；无路由时返回 404 而非 401）</li>
     *   <li>/actuator、/actuator/health、/actuator/info、/actuator/metrics、/actuator/metrics/**、
     *       /actuator/circuitbreakers、/actuator/prometheus — Actuator 监控端点；
     *       管理端口默认绑定 127.0.0.1，由 MANAGEMENT_SERVER_ADDRESS 控制访问范围，
     *       仅本机访问，安全风险可控。Prometheus 抓取端点必须放行以满足指标采集需求；
     *       /actuator 仅返回端点目录索引，其余未列出端点仍要求管理员角色（精确放行）</li>
     *   <li>/swagger-ui/**、/v3/api-docs/** — API 文档接口</li>
     *   <li>/uploads/apex/** — 二选一和四选二使用的 AF/AM 投票照片静态资源（本地存储）</li>
     *   <li>/tos/** — 火山云 TOS 照片代理路径（通过预签名 URL 302 重定向访问）</li>
     *   <li>/minio/** — MinIO 照片代理路径（通过预签名 URL 302 重定向访问）</li>
     *   <li>/api/v2/file-shares/exchange、/api/v2/file-shares/download —
     *       文件分享链接的匿名兑换与下载入口；令牌经 POST 请求体传递，租户与文件归属
     *       由服务端分享记录确定。创建与撤销分享仍需认证</li>
     * </ul>
     *
     */
    public static final List<String> PUBLIC_PATHS = List.of(
            "/",
            "/api/v1/auth/login",
            "/api/v1/auth/wechat/mp/authorize",
            "/api/v1/office/login",
            "/api/v1/office/callback",
            "/api/v1/auth/register",
            "/api/v1/auth/wechat/bind-phone",
            "/api/v1/auth/refresh",
            "/api/v1/auth/sms/code",
            "/api/v1/auth/cl/sms/code",
            "/api/v1/auth/cl/validate-mobile",
            "/oauth2/token",
            "/oauth2/revoke",
            "/oauth2/introspect",
            "/api/v1/auth/captcha",
            "/api/v1/auth/captcha/**",
            "/api/v1/onboarding/policies",
            "/api/v1/health",
            "/health",
            "/actuator",
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/actuator/info",
            "/actuator/metrics",
            "/actuator/metrics/**",
            "/actuator/circuitbreakers",
            "/actuator/prometheus",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/webjars/**",
            "/doc.html",
            "/uploads/apex/**",
            "/tos/**",
            "/minio/**",
            "/api/example/**",
            "/api/v1/auth/oidc/login",
            "/api/v1/auth/social/*/login",
            "/api/v1/auth/oidc/callback",
            "/oauth2/authorization/**",
            "/login/oauth2/code/**",
            "/api/v2/file-shares/exchange",
            "/api/v2/file-shares/download");
}
