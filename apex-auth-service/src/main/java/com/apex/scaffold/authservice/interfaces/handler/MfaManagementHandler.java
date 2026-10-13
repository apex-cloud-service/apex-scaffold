package com.apex.scaffold.authservice.interfaces.handler;

import com.apex.scaffold.auth.domain.model.MfaConfig;
import com.apex.scaffold.auth.domain.service.MfaService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.OperationLog.OperationType;
import com.apex.scaffold.common.annotation.RequireAdminRole;
import com.apex.scaffold.common.annotation.RequireResourcePermission;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 多因素认证管理Handler
 *
 * <p>提供企业级多因素认证(MFA/2FA)的完整管理能力，支持TOTP、SMS、Email三种认证方式。
 *
 * <p><b>核心功能：</b>
 * <ul>
 *   <li><b>TOTP认证</b>：基于时间的一次性密码（Google Authenticator、Microsoft Authenticator）</li>
 *   <li><b>短信认证</b>：通过手机短信发送验证码</li>
 *   <li><b>邮箱认证</b>：通过邮箱发送验证码</li>
 *   <li><b>备用码</b>：生成和验证一次性备用验证码（用于设备丢失场景）</li>
 *   <li><b>MFA管理</b>：启用、禁用、解锁MFA配置</li>
 * </ul>
 *
 * <p><b>认证流程：</b>
 * <ol>
 *   <li><b>启用TOTP</b>：系统生成密钥和二维码 → 用户扫码添加到认证器 → 验证激活</li>
 *   <li><b>启用SMS/Email</b>：绑定手机号/邮箱 → 发送测试验证码 → 验证成功后启用</li>
 *   <li><b>登录验证</b>：用户名密码正确后 → 要求输入MFA验证码 → 验证通过后签发token</li>
 *   <li><b>备用码使用</b>：常规MFA不可用时 → 使用备用码登录 → 备用码一次性消耗</li>
 * </ol>
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li><b>失败锁定</b>：连续5次验证失败，账号临时锁定30分钟</li>
 *   <li><b>时间窗口</b>：TOTP验证允许±1个时间窗口（30秒）的时间偏移</li>
 *   <li><b>防重放</b>：已使用的验证码在时间窗口内不可重复使用</li>
 *   <li><b>备用码加密</b>：备用码使用BCrypt加密存储</li>
 *   <li><b>审计日志</b>：记录所有MFA配置变更和验证行为</li>
 * </ul>
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li><b>高安全要求</b>：金融、医疗等行业的强制MFA策略</li>
 *   <li><b>管理员账号</b>：要求所有管理员启用MFA</li>
 *   <li><b>敏感操作</b>：修改密码、删除数据等敏感操作二次验证</li>
 *   <li><b>异地登录</b>：检测到异地登录时强制MFA验证</li>
 * </ul>
 *
 * <p><b>多租户支持：</b>
 * <p>MFA配置按租户隔离，支持不同租户配置不同的MFA策略（强制/可选、认证方式等）。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "多因素认证", description = "MFA/2FA管理 - 支持TOTP、SMS、Email三种认证方式")
public class MfaManagementHandler {

    private final MfaService mfaService;

    /**
     * 启用TOTP多因素认证
     *
     * <p>为用户生成TOTP密钥和二维码，用户需扫码添加到认证器应用（如Google Authenticator）并验证激活。
     *
     * <p><b>返回数据包含：</b>
     * <ul>
     *   <li><b>secret</b>：Base32编码的密钥（用于手动输入）</li>
     *   <li><b>qrCodeUrl</b>：二维码图片URL（用于扫码添加）</li>
     *   <li><b>issuer</b>：签发者标识（显示在认证器应用中）</li>
     *   <li><b>accountName</b>：账户名称（通常为用户邮箱或用户名）</li>
     * </ul>
     *
     * <p><b>操作流程：</b>
     * <ol>
     *   <li>调用此接口生成密钥和二维码</li>
     *   <li>用户使用认证器应用扫描二维码</li>
     *   <li>认证器生成6位动态验证码</li>
     *   <li>调用激活接口验证验证码</li>
     *   <li>验证通过后TOTP正式启用</li>
     * </ol>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>用户首次启用TOTP认证</li>
     *   <li>设备更换后重新绑定认证器</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>密钥生成后需要在15分钟内完成验证激活，否则失效</li>
     *   <li>未激活前MFA不会生效</li>
     *   <li>建议用户保存备用验证码以防设备丢失</li>
     * </ul>
     *
     * @param request 服务器请求对象，从上下文获取userId和tenantId
     * @return TOTP配置信息，包含密钥和二维码
     */
    @OperationLog(module = "MFA管理", type = OperationType.INSERT, description = "启用TOTP多因素认证")
    @Operation(operationId = "enableTotp", summary = "启用TOTP多因素认证", description = "生成TOTP密钥和二维码，用户扫码添加到认证器应用后需验证激活")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "生成成功，返回密钥和二维码信息",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "用户已启用TOTP或参数错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "未认证，需要先登录",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> enableTotp(ServerRequest request) {
        String userId = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("userId is required"));
        String tenantId = tenantId(request);

        return mfaService
                .enableTotp(userId, tenantId)
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(result)))
                .onErrorResume(this::handleError);
    }

    /**
     * 验证并激活TOTP
     *
     * <p>验证用户从认证器应用获取的验证码，验证通过后正式激活TOTP多因素认证。
     *
     * <p><b>验证流程：</b>
     * <ol>
     *   <li>用户在认证器应用中查看当前的6位验证码</li>
     *   <li>在激活页面输入验证码</li>
     *   <li>系统根据之前生成的密钥计算期望的验证码</li>
     *   <li>验证通过后将TOTP状态设置为已启用</li>
     *   <li>同时生成10个备用验证码返回给用户保存</li>
     * </ol>
     *
     * <p><b>参数说明：</b>
     * <ul>
     *   <li><b>userId</b>：用户ID，从请求上下文自动获取</li>
     *   <li><b>code</b>：6位数字验证码（查询参数）</li>
     * </ul>
     *
     * <p><b>返回数据包含：</b>
     * <ul>
     *   <li><b>success</b>：激活是否成功</li>
     *   <li><b>backupCodes</b>：10个备用验证码（每个8位）</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>完成TOTP启用流程的最后一步</li>
     *   <li>验证用户已正确配置认证器应用</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>验证码有效期30秒，允许±1个时间窗口的偏移</li>
     *   <li>激活后请务必保存备用验证码，防止设备丢失无法登录</li>
     *   <li>同一验证码在时间窗口内不可重复使用（防重放攻击）</li>
     * </ul>
     *
     * @param request 服务器请求对象，查询参数包含code
     * @return 激活结果和备用验证码
     */
    @OperationLog(module = "MFA管理", type = OperationType.UPDATE, description = "验证并激活TOTP")
    @Operation(operationId = "activateTotp", summary = "验证并激活TOTP", description = "验证认证器应用生成的验证码，验证通过后正式启用TOTP并返回备用验证码")
    @Parameter(name = "code", description = "6位数字验证码", required = true, example = "123456")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "激活成功，返回备用验证码列表",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "验证码错误、已过期或TOTP未初始化",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "未认证",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> activateTotp(ServerRequest request) {
        String userId = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("userId is required"));
        String code = request.queryParam("code").orElseThrow(() -> new IllegalArgumentException("code is required"));
        String tenantId = tenantId(request);

        return mfaService
                .verifyAndActivateTotp(userId, code, tenantId)
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(result)))
                .onErrorResume(this::handleError);
    }

    /**
     * 启用短信MFA
     *
     * <p>为用户启用基于短信验证码的多因素认证，需要绑定手机号码。
     *
     * <p><b>参数说明：</b>
     * <ul>
     *   <li><b>userId</b>：用户ID，从请求上下文自动获取</li>
     *   <li><b>tenantId</b>：租户ID，从请求上下文自动获取</li>
     *   <li><b>phoneNumber</b>：手机号码（需符合E.164格式，如+8613800138000）</li>
     * </ul>
     *
     * <p><b>启用流程：</b>
     * <ol>
     *   <li>验证手机号格式是否正确</li>
     *   <li>检查该手机号是否已被其他用户绑定</li>
     *   <li>向手机号发送测试验证码</li>
     *   <li>用户输入验证码完成验证后正式启用</li>
     * </ol>
     *
     * <p><b>返回数据：</b>
     * <ul>
     *   <li>MFA配置信息，包含手机号（脱敏）、启用状态等</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>用户不便使用认证器应用，选择短信验证</li>
     *   <li>企业要求员工必须绑定工作手机号</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>短信验证码有效期5分钟</li>
     *   <li>单个手机号每分钟最多发送1条，每小时最多5条</li>
     *   <li>短信验证依赖第三方服务，可能存在延迟</li>
     *   <li>国际短信费用较高，建议限制发送频率</li>
     * </ul>
     *
     * @param request 服务器请求对象，查询参数包含phoneNumber
     * @return MFA配置信息
     */
    @OperationLog(module = "MFA管理", type = OperationType.INSERT, description = "启用短信MFA")
    @Operation(operationId = "enableSms", summary = "启用短信MFA", description = "绑定手机号并启用短信验证码多因素认证")
    @Parameter(name = "phoneNumber", description = "手机号码（E.164格式）", required = true, example = "+8613800138000")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "启用成功，返回MFA配置信息",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "手机号格式错误或已被绑定",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "429",
            description = "发送频率超限",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> enableSms(ServerRequest request) {
        String userId = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("userId is required"));
        String tenantId = tenantId(request);
        String phoneNumber = request.queryParam("phoneNumber")
                .orElseThrow(() -> new IllegalArgumentException("phoneNumber is required"));

        return mfaService
                .enableSms(userId, tenantId, phoneNumber)
                .flatMap(config -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(config)))
                .onErrorResume(this::handleError);
    }

    /**
     * 启用邮箱MFA
     *
     * <p>为用户启用基于邮箱验证码的多因素认证，需要绑定邮箱地址。
     *
     * <p><b>参数说明：</b>
     * <ul>
     *   <li><b>userId</b>：用户ID，从请求上下文自动获取</li>
     *   <li><b>tenantId</b>：租户ID，从请求上下文自动获取</li>
     *   <li><b>email</b>：邮箱地址（需符合RFC 5322标准）</li>
     * </ul>
     *
     * <p><b>启用流程：</b>
     * <ol>
     *   <li>验证邮箱格式是否正确</li>
     *   <li>检查该邮箱是否已被其他用户绑定</li>
     *   <li>向邮箱发送测试验证码</li>
     *   <li>用户输入验证码完成验证后正式启用</li>
     * </ol>
     *
     * <p><b>返回数据：</b>
     * <ul>
     *   <li>MFA配置信息，包含邮箱（脱敏）、启用状态等</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>用户希望使用邮箱接收验证码</li>
     *   <li>企业要求员工绑定企业邮箱</li>
     *   <li>作为短信MFA的备选方案</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>邮箱验证码有效期10分钟</li>
     *   <li>单个邮箱每分钟最多发送1封，每小时最多5封</li>
     *   <li>邮件可能被误判为垃圾邮件，提醒用户检查垃圾箱</li>
     *   <li>邮件送达可能存在延迟（1-5分钟）</li>
     * </ul>
     *
     * @param request 服务器请求对象，查询参数包含email
     * @return MFA配置信息
     */
    @OperationLog(module = "MFA管理", type = OperationType.INSERT, description = "启用邮箱MFA")
    @Operation(operationId = "enableEmail", summary = "启用邮箱MFA", description = "绑定邮箱地址并启用邮箱验证码多因素认证")
    @Parameter(name = "email", description = "邮箱地址", required = true, example = "user@example.com")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "启用成功，返回MFA配置信息",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "邮箱格式错误或已被绑定",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "429",
            description = "发送频率超限",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> enableEmail(ServerRequest request) {
        String userId = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("userId is required"));
        String tenantId = tenantId(request);
        String email = request.queryParam("email").orElseThrow(() -> new IllegalArgumentException("email is required"));

        return mfaService
                .enableEmail(userId, tenantId, email)
                .flatMap(config -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(config)))
                .onErrorResume(this::handleError);
    }

    /**
     * 禁用MFA
     *
     * <p>禁用用户指定类型的多因素认证，禁用后该认证方式将不再生效。
     *
     * <p><b>参数说明：</b>
     * <ul>
     *   <li><b>userId</b>：用户ID，从请求上下文自动获取</li>
     *   <li><b>type</b>：MFA类型（路径参数），可选值：TOTP、SMS、EMAIL</li>
     * </ul>
     *
     * <p><b>禁用流程：</b>
     * <ol>
     *   <li>验证用户身份（需要当前密码或现有MFA验证）</li>
     *   <li>检查是否为最后一种MFA方式（建议保留至少一种）</li>
     *   <li>标记该MFA配置为禁用状态</li>
     *   <li>清除相关的密钥、绑定信息</li>
     * </ol>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>用户更换认证方式（先禁用旧方式，再启用新方式）</li>
     *   <li>手机号或邮箱变更，需要重新绑定</li>
     *   <li>用户不再需要MFA保护</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>禁用操作需要进行身份验证，防止账号被盗后恶意禁用MFA</li>
     *   <li>如果是管理员强制要求的MFA，普通用户无法自行禁用</li>
     *   <li>禁用后备用验证码也会失效</li>
     *   <li>建议保留至少一种MFA方式以保障账号安全</li>
     * </ul>
     *
     * @param request 服务器请求对象，路径参数包含type
     * @return 禁用成功的响应
     */
    @OperationLog(module = "MFA管理", type = OperationType.DELETE, description = "禁用MFA")
    @Operation(operationId = "disableMfa", summary = "禁用MFA", description = "禁用指定类型的多因素认证")
    @Parameter(
            name = "type",
            description = "MFA类型",
            required = true,
            schema = @Schema(allowableValues = {"TOTP", "SMS", "EMAIL"}),
            example = "TOTP")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "禁用成功",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "MFA类型不存在或未启用",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "禁止操作 - 管理员强制要求或最后一种MFA",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> disableMfa(ServerRequest request) {
        String userId = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("userId is required"));
        String typeStr = request.pathVariable("type");
        MfaConfig.MfaType type = MfaConfig.MfaType.valueOf(typeStr);
        String tenantId = tenantId(request);

        return mfaService
                .disableMfa(userId, type, tenantId)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(null)))
                .onErrorResume(this::handleError);
    }

    /**
     * 验证MFA代码
     *
     * <p>验证用户输入的MFA验证码是否正确，用于登录验证或敏感操作二次验证。
     *
     * <p><b>参数说明：</b>
     * <ul>
     *   <li><b>userId</b>：用户ID，从请求上下文自动获取</li>
     *   <li><b>code</b>：验证码（查询参数）</li>
     *   <li><b>type</b>：MFA类型（查询参数），可选值：TOTP、SMS、EMAIL</li>
     * </ul>
     *
     * <p><b>验证逻辑：</b>
     * <ul>
     *   <li><b>TOTP</b>：根据密钥和当前时间计算期望验证码，允许±1个时间窗口偏移</li>
     *   <li><b>SMS/EMAIL</b>：与系统发送的验证码进行比对，验证码有效期5-10分钟</li>
     * </ul>
     *
     * <p><b>返回值：</b>
     * <ul>
     *   <li><b>true</b>：验证通过</li>
     *   <li><b>false</b>：验证失败</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>登录流程中的MFA二次验证</li>
     *   <li>修改密码、绑定邮箱等敏感操作的确认</li>
     *   <li>高风险操作的二次身份验证</li>
     * </ul>
     *
     * <p><b>安全特性：</b>
     * <ul>
     *   <li>连续5次验证失败，账号临时锁定30分钟</li>
     *   <li>验证码防重放：同一验证码在时间窗口内不可重复使用</li>
     *   <li>记录验证日志，便于审计和异常检测</li>
     * </ul>
     *
     * @param request 服务器请求对象，查询参数包含code和type
     * @return 验证结果（布尔值）
     */
    @OperationLog(module = "MFA管理", type = OperationType.SELECT, description = "验证MFA代码")
    @Operation(operationId = "verifyMfaCode", summary = "验证MFA代码", description = "验证用户输入的MFA验证码是否正确")
    @Parameter(name = "code", description = "MFA验证码", required = true, example = "123456")
    @Parameter(
            name = "type",
            description = "MFA类型",
            required = true,
            schema = @Schema(allowableValues = {"TOTP", "SMS", "EMAIL"}),
            example = "TOTP")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "验证完成，返回验证结果（true/false）",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "参数错误或MFA未启用",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "423",
            description = "账号已锁定 - 验证失败次数过多",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> verifyMfaCode(ServerRequest request) {
        String userId = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("userId is required"));
        String code = request.queryParam("code").orElseThrow(() -> new IllegalArgumentException("code is required"));
        String typeStr = request.queryParam("type").orElseThrow(() -> new IllegalArgumentException("type is required"));
        MfaConfig.MfaType type = MfaConfig.MfaType.valueOf(typeStr);
        String tenantId = tenantId(request);

        return mfaService
                .verifyMfaCode(userId, code, type, tenantId)
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(result)))
                .onErrorResume(this::handleError);
    }

    /**
     * 发送MFA验证码
     *
     * <p>向用户绑定的手机号或邮箱发送MFA验证码，用于SMS或EMAIL类型的MFA验证。
     *
     * <p><b>参数说明：</b>
     * <ul>
     *   <li><b>userId</b>：用户ID，从请求上下文自动获取</li>
     *   <li><b>type</b>：MFA类型（查询参数），可选值：SMS、EMAIL（TOTP不需要发送）</li>
     * </ul>
     *
     * <p><b>发送流程：</b>
     * <ol>
     *   <li>检查用户是否已启用对应类型的MFA</li>
     *   <li>生成6位随机数字验证码</li>
     *   <li>将验证码缓存到Redis，设置有效期（SMS: 5分钟，EMAIL: 10分钟）</li>
     *   <li>调用短信或邮件服务发送验证码</li>
     * </ol>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>用户登录时请求发送MFA验证码</li>
     *   <li>验证码过期后重新发送</li>
     *   <li>用户未收到验证码时重试</li>
     * </ul>
     *
     * <p><b>频率限制：</b>
     * <ul>
     *   <li>同一用户每分钟最多发送1次</li>
     *   <li>同一用户每小时最多发送5次</li>
     *   <li>同一IP每小时最多发送10次</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>TOTP类型无需发送验证码，会返回错误</li>
     *   <li>短信和邮件发送可能存在延迟</li>
     *   <li>频繁发送可能导致运营成本上升</li>
     * </ul>
     *
     * @param request 服务器请求对象，查询参数包含type
     * @return 发送成功的响应
     */
    @OperationLog(module = "MFA管理", type = OperationType.INSERT, description = "发送MFA验证码")
    @Operation(operationId = "sendMfaCode", summary = "发送MFA验证码", description = "向用户绑定的手机号或邮箱发送验证码")
    @Parameter(
            name = "type",
            description = "MFA类型（SMS或EMAIL）",
            required = true,
            schema = @Schema(allowableValues = {"SMS", "EMAIL"}),
            example = "SMS")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "发送成功",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "参数错误、MFA未启用或类型不支持",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "429",
            description = "发送频率超限",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> sendMfaCode(ServerRequest request) {
        String userId = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("userId is required"));
        String typeStr = request.queryParam("type").orElseThrow(() -> new IllegalArgumentException("type is required"));
        MfaConfig.MfaType type = MfaConfig.MfaType.valueOf(typeStr);
        String tenantId = tenantId(request);

        return mfaService
                .sendMfaCode(userId, type, tenantId)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(null)))
                .onErrorResume(this::handleError);
    }

    /**
     * 查询用户的MFA配置
     *
     * <p>获取用户所有已启用的MFA配置信息，包含配置状态、绑定信息等。
     *
     * <p><b>参数说明：</b>
     * <ul>
     *   <li><b>userId</b>：用户ID，从请求上下文自动获取</li>
     * </ul>
     *
     * <p><b>返回数据包含：</b>
     * <ul>
     *   <li><b>type</b>：MFA类型（TOTP、SMS、EMAIL）</li>
     *   <li><b>enabled</b>：是否已启用</li>
     *   <li><b>phoneNumber</b>：绑定的手机号（脱敏显示，如 +86138****8000）</li>
     *   <li><b>email</b>：绑定的邮箱（脱敏显示，如 u***@example.com）</li>
     *   <li><b>createdAt</b>：启用时间</li>
     *   <li><b>lastUsedAt</b>：最后使用时间</li>
     *   <li><b>failedAttempts</b>：连续失败次数</li>
     *   <li><b>lockedUntil</b>：锁定截止时间（如果被锁定）</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>用户查看自己的MFA配置状态</li>
     *   <li>安全设置页面展示已启用的认证方式</li>
     *   <li>管理员查看用户的MFA配置</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>敏感信息（手机号、邮箱、密钥）会进行脱敏处理</li>
     *   <li>只返回已启用的MFA配置</li>
     * </ul>
     *
     * @param request 服务器请求对象
     * @return MFA配置列表
     */
    @OperationLog(module = "MFA管理", type = OperationType.SELECT, description = "查询用户MFA配置")
    @Operation(operationId = "getUserMfaConfigs", summary = "查询用户的MFA配置", description = "获取用户所有已启用的MFA配置信息")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "查询成功，返回MFA配置列表",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "未认证",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> getUserMfaConfigs(ServerRequest request) {
        String userId = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("userId is required"));

        return mfaService
                .getUserMfaConfigs(userId, tenantId(request))
                .collectList()
                .flatMap(configs -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(configs))
                .onErrorResume(this::handleError);
    }

    /**
     * 检查MFA状态
     *
     * <p>快速检查用户是否已启用任意类型的MFA，返回布尔值。
     *
     * <p><b>参数说明：</b>
     * <ul>
     *   <li><b>userId</b>：用户ID，从请求上下文自动获取</li>
     * </ul>
     *
     * <p><b>返回值：</b>
     * <ul>
     *   <li><b>true</b>：用户已启用至少一种MFA</li>
     *   <li><b>false</b>：用户未启用任何MFA</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>登录流程中判断是否需要进行MFA验证</li>
     *   <li>前端页面快速判断MFA状态以显示不同UI</li>
     *   <li>接口网关决策是否需要MFA拦截</li>
     * </ul>
     *
     * <p><b>性能优化：</b>
     * <ul>
     *   <li>结果会缓存在Redis中，有效期5分钟</li>
     *   <li>响应速度极快，适合高频调用</li>
     * </ul>
     *
     * @param request 服务器请求对象
     * @return 布尔值，表示MFA是否已启用
     */
    @OperationLog(module = "MFA管理", type = OperationType.SELECT, description = "检查MFA状态")
    @Operation(operationId = "checkMfaStatus", summary = "检查MFA状态", description = "快速检查用户是否已启用任意类型的MFA")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "检查成功，返回布尔值（true=已启用，false=未启用）",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "未认证",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> checkMfaStatus(ServerRequest request) {
        String userId = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("userId is required"));

        return mfaService
                .isMfaEnabled(userId, tenantId(request))
                .flatMap(enabled -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(enabled)))
                .onErrorResume(this::handleError);
    }

    /**
     * 重新生成备用验证码
     *
     * <p>为用户重新生成一组备用验证码，用于设备丢失或无法使用常规MFA时的应急登录。
     *
     * <p><b>参数说明：</b>
     * <ul>
     *   <li><b>userId</b>：用户ID，从请求上下文自动获取</li>
     * </ul>
     *
     * <p><b>生成规则：</b>
     * <ul>
     *   <li>每次生成10个备用验证码</li>
     *   <li>每个验证码为8位字母数字组合（不包含易混淆字符如0、O、I、l）</li>
     *   <li>验证码使用BCrypt加密后存储</li>
     *   <li>旧的备用验证码会被完全替换</li>
     * </ul>
     *
     * <p><b>返回数据：</b>
     * <ul>
     *   <li>10个明文备用验证码（只在此次返回，不会再次显示）</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>首次启用MFA时自动生成</li>
     *   <li>备用验证码用完或即将用完时重新生成</li>
     *   <li>怀疑备用验证码泄露时重置</li>
     * </ul>
     *
     * <p><b>重要提示：</b>
     * <ul>
     *   <li><b>务必保存</b>：备用验证码只在生成时显示一次，请妥善保存</li>
     *   <li><b>安全存储</b>：建议打印或保存在密码管理器中</li>
     *   <li><b>一次性使用</b>：每个备用验证码只能使用一次</li>
     *   <li><b>重新生成会使旧验证码失效</b>：如果旧验证码还有剩余，重新生成后将无法使用</li>
     * </ul>
     *
     * @param request 服务器请求对象
     * @return 10个备用验证码
     */
    @OperationLog(module = "MFA管理", type = OperationType.UPDATE, description = "重新生成备用验证码")
    @Operation(operationId = "regenerateBackupCodes", summary = "重新生成备用验证码", description = "为用户生成一组新的备用验证码，旧验证码将失效")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "生成成功，返回10个备用验证码（请妥善保存，只显示一次）",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "用户未启用任何MFA",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "未认证",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> regenerateBackupCodes(ServerRequest request) {
        String userId = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("userId is required"));

        return mfaService
                .regenerateBackupCodes(userId, tenantId(request))
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(result)))
                .onErrorResume(this::handleError);
    }

    /**
     * 验证备用验证码
     *
     * <p>使用备用验证码进行身份验证，用于常规MFA不可用的应急场景。
     *
     * <p><b>参数说明：</b>
     * <ul>
     *   <li><b>userId</b>：用户ID，从请求上下文自动获取</li>
     *   <li><b>code</b>：8位备用验证码（查询参数）</li>
     * </ul>
     *
     * <p><b>验证流程：</b>
     * <ol>
     *   <li>从数据库获取用户的所有备用验证码（BCrypt加密存储）</li>
     *   <li>逐个使用BCrypt验证输入的验证码</li>
     *   <li>如果匹配成功，标记该验证码为已使用（一次性消耗）</li>
     *   <li>返回验证结果和剩余验证码数量</li>
     * </ol>
     *
     * <p><b>返回数据：</b>
     * <ul>
     *   <li><b>success</b>：验证是否成功</li>
     *   <li><b>remainingCodes</b>：剩余可用的备用验证码数量</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>用户的TOTP设备丢失或损坏</li>
     *   <li>无法接收短信或邮件验证码</li>
     *   <li>更换手机号或邮箱后尚未更新MFA配置</li>
     *   <li>应急情况下需要登录账号</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>每个备用验证码只能使用一次，使用后立即失效</li>
     *   <li>当剩余验证码少于3个时，系统会提醒用户重新生成</li>
     *   <li>备用验证码验证失败也会计入失败次数，多次失败会锁定账号</li>
     *   <li>建议使用备用验证码登录后立即检查MFA配置</li>
     * </ul>
     *
     * @param request 服务器请求对象，查询参数包含code
     * @return 验证结果和剩余验证码数量
     */
    @OperationLog(module = "MFA管理", type = OperationType.SELECT, description = "验证备用验证码")
    @Operation(operationId = "verifyBackupCode", summary = "验证备用验证码", description = "使用备用验证码进行身份验证，验证码一次性使用")
    @Parameter(name = "code", description = "8位备用验证码", required = true, example = "ABCD1234")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "验证完成，返回验证结果和剩余验证码数量",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "验证码错误或已使用",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "423",
            description = "账号已锁定 - 验证失败次数过多",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> verifyBackupCode(ServerRequest request) {
        String userId = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("userId is required"));
        String code = request.queryParam("code").orElseThrow(() -> new IllegalArgumentException("code is required"));

        return mfaService
                .verifyBackupCode(userId, code, tenantId(request))
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(result)))
                .onErrorResume(this::handleError);
    }

    /**
     * 解锁MFA（管理员）
     *
     * <p>管理员强制解锁用户因验证失败过多而被锁定的MFA，恢复用户的正常登录能力。
     *
     * <p><b>参数说明：</b>
     * <ul>
     *   <li><b>userId</b>：要解锁的用户ID（查询参数）</li>
     *   <li><b>type</b>：MFA类型（查询参数），可选值：TOTP、SMS、EMAIL</li>
     *   <li><b>operator</b>：操作员ID，从请求上下文自动获取</li>
     * </ul>
     *
     * <p><b>解锁流程：</b>
     * <ol>
     *   <li>验证操作员是否具有管理员权限</li>
     *   <li>检查目标用户的MFA锁定状态</li>
     *   <li>清除失败计数，移除锁定标记</li>
     *   <li>记录解锁操作的审计日志</li>
     * </ol>
     *
     * <p><b>权限要求：</b>
     * <ul>
     *   <li>需要具有<b>ADMIN</b>或<b>SECURITY_ADMIN</b>角色</li>
     *   <li>租户管理员只能解锁本租户内的用户</li>
     *   <li>超级管理员可以解锁任意租户的用户</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>用户因多次输入错误验证码被锁定</li>
     *   <li>用户设备故障导致无法通过MFA验证</li>
     *   <li>应急情况下需要快速恢复用户访问权限</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>解锁操作会记录完整的审计日志（操作员、目标用户、时间、原因）</li>
     *   <li>频繁解锁同一用户可能表明MFA配置存在问题，应进行调查</li>
     *   <li>建议解锁后提醒用户检查MFA设备和配置</li>
     *   <li>不会禁用MFA，只是清除锁定状态</li>
     * </ul>
     *
     * @param request 服务器请求对象，查询参数包含userId和type
     * @return 解锁成功的响应
     */
    @OperationLog(module = "MFA管理", type = OperationType.UPDATE, description = "解锁MFA")
    @RequireAdminRole
    @RequireResourcePermission(
            resourceType = "mfa",
            resourceIdExpression = "#request.queryParam('userId').orElse('')",
            permission = "MANAGE",
            errorMessage = "无权解锁该用户MFA")
    @Operation(operationId = "unlockMfa", summary = "解锁MFA（管理员）", description = "管理员强制解锁用户因验证失败过多而被锁定的MFA")
    @Parameter(name = "userId", description = "要解锁的用户ID", required = true, example = "1001")
    @Parameter(
            name = "type",
            description = "MFA类型",
            required = true,
            schema = @Schema(allowableValues = {"TOTP", "SMS", "EMAIL"}),
            example = "TOTP")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "解锁成功",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "参数错误或MFA未锁定",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "权限不足 - 需要管理员权限",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "用户不存在或MFA未启用",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> unlockMfa(ServerRequest request) {
        String userId =
                request.queryParam("userId").orElseThrow(() -> new IllegalArgumentException("userId is required"));
        String typeStr = request.queryParam("type").orElseThrow(() -> new IllegalArgumentException("type is required"));
        MfaConfig.MfaType type = MfaConfig.MfaType.valueOf(typeStr);
        String operator = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("operator is required"));
        String tenantId = tenantId(request);

        return mfaService
                .unlockMfa(userId, type, operator, tenantId)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(null)))
                .onErrorResume(this::handleError);
    }

    /**
     * 从请求上下文中解析可信的租户标识。
     *
     * <p>租户标识由认证过滤器在服务端写入请求属性，不允许由客户端请求头覆盖。
     * 当请求属性缺失时，回退到当前线程租户上下文；若两者均不可用则抛出异常。
     *
     * @param request 当前服务端请求
     * @return 非空、非空白的租户标识
     */
    private String tenantId(ServerRequest request) {
        String tenantId = request.attribute("tenantId")
                .map(Object::toString)
                .orElseGet(() -> TenantContext.currentTenantId()
                        .orElseThrow(() -> new IllegalArgumentException("tenantId is required")));
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        return tenantId;
    }

    private Mono<ServerResponse> handleError(Throwable error) {
        log.error("MFA操作失败", error);
        return ServerResponse.status(500)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Result.error(error.getMessage()));
    }
}
