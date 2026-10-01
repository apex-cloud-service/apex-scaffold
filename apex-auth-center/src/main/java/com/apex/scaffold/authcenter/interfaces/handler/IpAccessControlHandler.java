package com.apex.scaffold.authcenter.interfaces.handler;

import com.apex.scaffold.authcenter.domain.model.IpAccessControl;
import com.apex.scaffold.authcenter.domain.service.IpAccessControlService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.OperationLog.OperationType;
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
 * IP访问控制管理Handler
 *
 * <p>提供基于IP地址的访问控制能力，支持白名单和黑名单两种策略。
 *
 * <p><b>核心功能：</b>
 * <ul>
 *   <li><b>白名单管理</b>：添加、移除、查询白名单IP，白名单IP始终允许访问</li>
 *   <li><b>黑名单管理</b>：添加、移除、查询黑名单IP，支持临时封禁（设置过期时间）</li>
 *   <li><b>规则管理</b>：启用、禁用、删除访问控制规则</li>
 *   <li><b>访问检查</b>：实时检查IP是否允许访问</li>
 *   <li><b>自动清理</b>：定期清理过期的黑名单规则</li>
 * </ul>
 *
 * <p><b>优先级规则：</b>
 * <ol>
 *   <li>黑名单优先：如果IP在黑名单中，直接拒绝访问（即使在白名单中）</li>
 *   <li>白名单次之：如果IP在白名单中且不在黑名单中，允许访问</li>
 *   <li>默认策略：既不在白名单也不在黑名单，根据系统配置决定（通常允许）</li>
 * </ol>
 *
 * <p><b>IP地址格式：</b>
 * <ul>
 *   <li>支持单个IP：192.168.1.100</li>
 *   <li>支持CIDR段：192.168.1.0/24</li>
 *   <li>支持IPv6：2001:0db8:85a3::8a2e:0370:7334</li>
 * </ul>
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li><b>恶意IP封禁</b>：临时或永久封禁攻击来源IP</li>
 *   <li><b>区域限制</b>：只允许特定国家/地区的IP访问</li>
 *   <li><b>内网访问</b>：仅允许内网IP段访问管理接口</li>
 *   <li><b>防暴力破解</b>：配合登录失败次数限制，自动封禁IP</li>
 *   <li><b>灰度发布</b>：为白名单IP提前开放新功能</li>
 * </ul>
 *
 * <p><b>多租户隔离：</b>
 * <p>所有规则都绑定租户ID，确保不同租户的IP策略互不影响。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "IP访问控制", description = "IP白名单/黑名单管理 - 提供基于IP的访问控制能力")
public class IpAccessControlHandler {

    private final IpAccessControlService ipAccessControlService;

    /**
     * 添加IP白名单
     *
     * <p>将指定IP地址添加到白名单，白名单IP将始终允许访问系统。
     *
     * <p><b>参数说明：</b>
     * <ul>
     *   <li><b>ipAddress</b>：IP地址（支持单IP或CIDR段），如"192.168.1.100"或"192.168.1.0/24"</li>
     *   <li><b>tenantId</b>：租户ID，确保多租户隔离</li>
     *   <li><b>description</b>：描述信息（可选），建议说明添加原因和用途</li>
     *   <li><b>operator</b>：操作人ID，自动从当前用户上下文获取</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>添加公司办公网IP段</li>
     *   <li>为特定合作伙伴开放API访问</li>
     *   <li>灰度发布时为测试IP开放新功能</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>白名单优先级低于黑名单，即使IP在白名单中，如在黑名单中仍会被拒绝</li>
     *   <li>CIDR段请谨慎使用，避免范围过大导致安全风险</li>
     *   <li>建议记录详细的描述信息，便于后期审计</li>
     * </ul>
     *
     * @param request 服务器请求对象，查询参数包含ipAddress、tenantId、description
     * @return 添加成功的规则信息
     */
    @OperationLog(module = "IP访问控制", type = OperationType.INSERT, description = "添加IP白名单")
    @Operation(operationId = "addToWhitelist", summary = "添加IP白名单", description = "将指定IP地址添加到白名单，白名单IP将始终允许访问系统")
    @Parameter(name = "ipAddress", description = "IP地址（支持单IP或CIDR段）", required = true, example = "192.168.1.100")
    @Parameter(name = "tenantId", description = "租户ID", required = true, example = "tenant_001")
    @Parameter(name = "description", description = "描述信息（可选）", required = false, example = "公司总部办公网")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "添加成功，返回规则信息",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "参数错误 - IP地址格式不正确或已存在",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "服务器内部错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> addToWhitelist(ServerRequest request) {
        String ipAddress = request.queryParam("ipAddress")
                .orElseThrow(() -> new IllegalArgumentException("ipAddress is required"));
        String tenantId =
                request.queryParam("tenantId").orElseThrow(() -> new IllegalArgumentException("tenantId is required"));
        String description = request.queryParam("description").orElse(null);
        String operator = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("operator is required"));

        return ipAccessControlService
                .addToWhitelist(ipAddress, tenantId, description, operator)
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(result)))
                .onErrorResume(this::handleError);
    }

    /**
     * 添加IP黑名单
     *
     * <p>将指定IP地址添加到黑名单，黑名单IP将被拒绝访问系统。支持临时封禁和永久封禁。
     *
     * <p><b>参数说明：</b>
     * <ul>
     *   <li><b>ipAddress</b>：IP地址（支持单IP或CIDR段），如"192.168.1.100"或"192.168.1.0/24"</li>
     *   <li><b>tenantId</b>：租户ID，确保多租户隔离</li>
     *   <li><b>description</b>：描述信息（可选），建议说明封禁原因</li>
     *   <li><b>durationMinutes</b>：封禁时长（分钟），null表示永久封禁</li>
     *   <li><b>operator</b>：操作人ID，自动从当前用户上下文获取</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>临时封禁恶意攻击IP（如暴力破解、DDoS攻击）</li>
     *   <li>永久封禁已知的黑产IP</li>
     *   <li>限制特定国家/地区的访问</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>黑名单优先级最高，即使IP在白名单中也会被拒绝</li>
     *   <li>临时封禁到期后规则自动失效</li>
     *   <li>谨慎使用CIDR段封禁，避免误伤正常用户</li>
     * </ul>
     *
     * @param request 服务器请求对象，查询参数包含ipAddress、tenantId、description、durationMinutes
     * @return 添加成功的规则信息
     */
    @OperationLog(module = "IP访问控制", type = OperationType.INSERT, description = "添加IP黑名单")
    @Operation(operationId = "addToBlacklist", summary = "添加IP黑名单", description = "将指定IP地址添加到黑名单，支持临时封禁（设置时长）或永久封禁")
    @Parameter(name = "ipAddress", description = "IP地址（支持单IP或CIDR段）", required = true, example = "203.0.113.5")
    @Parameter(name = "tenantId", description = "租户ID", required = true, example = "tenant_001")
    @Parameter(name = "description", description = "封禁原因描述", required = false, example = "暴力破解攻击")
    @Parameter(name = "durationMinutes", description = "封禁时长（分钟），不传或null表示永久封禁", required = false, example = "1440")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "添加成功，返回规则信息",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "参数错误 - IP地址格式不正确或已存在",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "服务器内部错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> addToBlacklist(ServerRequest request) {
        String ipAddress = request.queryParam("ipAddress")
                .orElseThrow(() -> new IllegalArgumentException("ipAddress is required"));
        String tenantId =
                request.queryParam("tenantId").orElseThrow(() -> new IllegalArgumentException("tenantId is required"));
        String description = request.queryParam("description").orElse(null);
        Integer durationMinutes =
                request.queryParam("durationMinutes").map(Integer::parseInt).orElse(null);
        String operator = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("operator is required"));

        return ipAccessControlService
                .addToBlacklist(ipAddress, tenantId, description, durationMinutes, operator)
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(result)))
                .onErrorResume(this::handleError);
    }

    /**
     * 移除IP白名单
     *
     * <p>将指定IP地址从白名单中移除，移除后该IP将按照默认策略处理访问请求。
     *
     * <p><b>参数说明：</b>
     * <ul>
     *   <li><b>ipAddress</b>：要移除的IP地址（必须完全匹配已添加的IP或CIDR段）</li>
     *   <li><b>tenantId</b>：租户ID</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>合作伙伴合作到期，移除其白名单IP</li>
     *   <li>员工离职，移除其办公IP</li>
     *   <li>测试完成，移除测试IP段</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>移除操作立即生效，该IP的下一次请求将不再享受白名单特权</li>
     *   <li>IP地址必须完全匹配，包括CIDR段格式</li>
     * </ul>
     *
     * @param request 服务器请求对象，查询参数包含ipAddress、tenantId
     * @return 移除成功的响应
     */
    @OperationLog(module = "IP访问控制", type = OperationType.DELETE, description = "移除IP白名单")
    @Operation(operationId = "removeFromWhitelist", summary = "移除IP白名单", description = "将指定IP地址从白名单中移除")
    @Parameter(name = "ipAddress", description = "要移除的IP地址", required = true, example = "192.168.1.100")
    @Parameter(name = "tenantId", description = "租户ID", required = true, example = "tenant_001")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "移除成功",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "参数错误 - IP地址不存在于白名单中",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "白名单规则不存在",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> removeFromWhitelist(ServerRequest request) {
        String ipAddress = request.queryParam("ipAddress")
                .orElseThrow(() -> new IllegalArgumentException("ipAddress is required"));
        String tenantId =
                request.queryParam("tenantId").orElseThrow(() -> new IllegalArgumentException("tenantId is required"));

        return ipAccessControlService
                .removeFromWhitelist(ipAddress, tenantId)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(null)))
                .onErrorResume(this::handleError);
    }

    /**
     * 移除IP黑名单
     *
     * <p>将指定IP地址从黑名单中移除，解除封禁，该IP将能够正常访问系统。
     *
     * <p><b>参数说明：</b>
     * <ul>
     *   <li><b>ipAddress</b>：要解封的IP地址（必须完全匹配已封禁的IP或CIDR段）</li>
     *   <li><b>tenantId</b>：租户ID</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>确认为误封，手动解除封禁</li>
     *   <li>用户申诉成功，恢复访问权限</li>
     *   <li>攻击行为停止，提前解除封禁</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>解封操作立即生效</li>
     *   <li>临时封禁规则到期后会自动移除，无需手动操作</li>
     *   <li>建议在系统审计日志中记录解封原因</li>
     * </ul>
     *
     * @param request 服务器请求对象，查询参数包含ipAddress、tenantId
     * @return 移除成功的响应
     */
    @OperationLog(module = "IP访问控制", type = OperationType.DELETE, description = "移除IP黑名单")
    @Operation(operationId = "removeFromBlacklist", summary = "移除IP黑名单", description = "将指定IP地址从黑名单中移除，解除封禁")
    @Parameter(name = "ipAddress", description = "要解封的IP地址", required = true, example = "203.0.113.5")
    @Parameter(name = "tenantId", description = "租户ID", required = true, example = "tenant_001")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "解封成功",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "参数错误 - IP地址不存在于黑名单中",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "黑名单规则不存在",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> removeFromBlacklist(ServerRequest request) {
        String ipAddress = request.queryParam("ipAddress")
                .orElseThrow(() -> new IllegalArgumentException("ipAddress is required"));
        String tenantId =
                request.queryParam("tenantId").orElseThrow(() -> new IllegalArgumentException("tenantId is required"));

        return ipAccessControlService
                .removeFromBlacklist(ipAddress, tenantId)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(null)))
                .onErrorResume(this::handleError);
    }

    /**
     * 查询IP白名单
     *
     * <p>获取指定租户的所有白名单IP规则列表。
     *
     * <p><b>返回数据包含：</b>
     * <ul>
     *   <li><b>ruleId</b>：规则唯一标识</li>
     *   <li><b>ipAddress</b>：IP地址或CIDR段</li>
     *   <li><b>description</b>：描述信息</li>
     *   <li><b>enabled</b>：是否启用</li>
     *   <li><b>createdAt</b>：创建时间</li>
     *   <li><b>createdBy</b>：创建人</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>管理后台展示所有白名单IP</li>
     *   <li>审计白名单配置</li>
     *   <li>导出白名单用于备份</li>
     * </ul>
     *
     * @param request 服务器请求对象，查询参数包含tenantId
     * @return 白名单规则列表
     */
    @OperationLog(module = "IP访问控制", type = OperationType.SELECT, description = "查询IP白名单")
    @Operation(operationId = "getWhitelist", summary = "查询IP白名单", description = "获取指定租户的所有白名单IP规则列表")
    @Parameter(name = "tenantId", description = "租户ID", required = true, example = "tenant_001")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "查询成功，返回白名单规则数组",
            content = @Content(schema = @Schema(implementation = IpAccessControl.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "参数错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> getWhitelist(ServerRequest request) {
        String tenantId =
                request.queryParam("tenantId").orElseThrow(() -> new IllegalArgumentException("tenantId is required"));

        return ipAccessControlService
                .getWhitelist(tenantId)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(list))
                .onErrorResume(this::handleError);
    }

    /**
     * 查询IP黑名单
     *
     * <p>获取指定租户的所有黑名单IP规则列表，包括永久封禁和临时封禁的IP。
     *
     * <p><b>返回数据包含：</b>
     * <ul>
     *   <li><b>ruleId</b>：规则唯一标识</li>
     *   <li><b>ipAddress</b>：IP地址或CIDR段</li>
     *   <li><b>description</b>：封禁原因描述</li>
     *   <li><b>enabled</b>：是否启用</li>
     *   <li><b>expiresAt</b>：过期时间（临时封禁），null表示永久封禁</li>
     *   <li><b>createdAt</b>：创建时间</li>
     *   <li><b>createdBy</b>：封禁操作人</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>管理后台展示所有被封禁的IP</li>
     *   <li>监控恶意IP封禁情况</li>
     *   <li>审计封禁记录</li>
     * </ul>
     *
     * @param request 服务器请求对象，查询参数包含tenantId
     * @return 黑名单规则列表
     */
    @OperationLog(module = "IP访问控制", type = OperationType.SELECT, description = "查询IP黑名单")
    @Operation(operationId = "getBlacklist", summary = "查询IP黑名单", description = "获取指定租户的所有黑名单IP规则列表，包括永久和临时封禁")
    @Parameter(name = "tenantId", description = "租户ID", required = true, example = "tenant_001")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "查询成功，返回黑名单规则数组",
            content = @Content(schema = @Schema(implementation = IpAccessControl.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "参数错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> getBlacklist(ServerRequest request) {
        String tenantId =
                request.queryParam("tenantId").orElseThrow(() -> new IllegalArgumentException("tenantId is required"));

        return ipAccessControlService
                .getBlacklist(tenantId)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(list))
                .onErrorResume(this::handleError);
    }

    /**
     * 启用规则
     *
     * <p>启用指定的IP访问控制规则，使其生效。被禁用的规则不会被应用到访问控制判断中。
     *
     * <p><b>参数说明：</b>
     * <ul>
     *   <li><b>ruleId</b>：规则ID（路径参数）</li>
     *   <li><b>operator</b>：操作人ID，自动从当前用户上下文获取</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>临时禁用的规则需要重新启用</li>
     *   <li>定期审查后决定恢复某条规则</li>
     *   <li>测试完成后启用生产环境规则</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>启用操作立即生效</li>
     *   <li>会记录操作审计日志</li>
     * </ul>
     *
     * @param request 服务器请求对象，路径参数包含ruleId
     * @return 启用成功的响应
     */
    @OperationLog(module = "IP访问控制", type = OperationType.UPDATE, description = "启用IP访问控制规则")
    @Operation(operationId = "enableRule", summary = "启用规则", description = "启用指定的IP访问控制规则，使其生效")
    @Parameter(name = "ruleId", description = "规则ID", required = true, example = "1001")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "启用成功",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "规则不存在",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> enableRule(ServerRequest request) {
        Long ruleId = Long.parseLong(request.pathVariable("ruleId"));
        String operator = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("operator is required"));

        return ipAccessControlService
                .enableRule(ruleId, operator)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(null)))
                .onErrorResume(this::handleError);
    }

    /**
     * 禁用规则
     *
     * <p>禁用指定的IP访问控制规则，使其暂时失效。禁用后规则不会被删除，可随时重新启用。
     *
     * <p><b>参数说明：</b>
     * <ul>
     *   <li><b>ruleId</b>：规则ID（路径参数）</li>
     *   <li><b>operator</b>：操作人ID，自动从当前用户上下文获取</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>临时停用某条规则进行测试</li>
     *   <li>怀疑某条规则配置有误，先禁用再排查</li>
     *   <li>特殊时期（如促销活动）临时放宽访问限制</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>禁用操作立即生效，该IP的下一次请求将不再应用此规则</li>
     *   <li>禁用不等于删除，规则记录仍然保留</li>
     *   <li>会记录操作审计日志</li>
     * </ul>
     *
     * @param request 服务器请求对象，路径参数包含ruleId
     * @return 禁用成功的响应
     */
    @OperationLog(module = "IP访问控制", type = OperationType.UPDATE, description = "禁用IP访问控制规则")
    @Operation(operationId = "disableRule", summary = "禁用规则", description = "禁用指定的IP访问控制规则，使其暂时失效")
    @Parameter(name = "ruleId", description = "规则ID", required = true, example = "1001")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "禁用成功",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "规则不存在",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> disableRule(ServerRequest request) {
        Long ruleId = Long.parseLong(request.pathVariable("ruleId"));
        String operator = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("operator is required"));

        return ipAccessControlService
                .disableRule(ruleId, operator)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(null)))
                .onErrorResume(this::handleError);
    }

    /**
     * 删除规则
     *
     * <p>永久删除指定的IP访问控制规则，删除后无法恢复。
     *
     * <p><b>参数说明：</b>
     * <ul>
     *   <li><b>ruleId</b>：规则ID（路径参数）</li>
     *   <li><b>operator</b>：操作人ID，自动从当前用户上下文获取</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>规则已经过期且不再需要</li>
     *   <li>误配置的规则需要清理</li>
     *   <li>定期清理历史规则</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>删除操作不可逆，请谨慎操作</li>
     *   <li>建议先禁用规则验证影响，确认无误后再删除</li>
     *   <li>会记录操作审计日志，便于追溯</li>
     *   <li>删除操作立即生效</li>
     * </ul>
     *
     * @param request 服务器请求对象，路径参数包含ruleId
     * @return 删除成功的响应
     */
    @OperationLog(module = "IP访问控制", type = OperationType.DELETE, description = "删除IP访问控制规则")
    @Operation(operationId = "deleteRule", summary = "删除规则", description = "永久删除指定的IP访问控制规则，删除后无法恢复")
    @Parameter(name = "ruleId", description = "规则ID", required = true, example = "1001")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "删除成功",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "规则不存在",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> deleteRule(ServerRequest request) {
        Long ruleId = Long.parseLong(request.pathVariable("ruleId"));
        String operator = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("operator is required"));

        return ipAccessControlService
                .deleteRule(ruleId, operator)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(null)))
                .onErrorResume(this::handleError);
    }

    /**
     * 清理过期规则
     *
     * <p>自动清理所有已过期的临时黑名单规则。通常由定时任务调用，也可以手动触发。
     *
     * <p><b>清理逻辑：</b>
     * <ul>
     *   <li>查找所有设置了过期时间且已过期的黑名单规则</li>
     *   <li>批量删除这些过期规则</li>
     *   <li>返回清理的规则数量</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>定时任务自动清理（推荐每小时执行一次）</li>
     *   <li>手动触发清理释放存储空间</li>
     *   <li>系统维护时批量清理历史数据</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>只清理黑名单规则，不影响白名单</li>
     *   <li>永久封禁的规则（无过期时间）不会被清理</li>
     *   <li>清理操作会记录审计日志</li>
     * </ul>
     *
     * @param request 服务器请求对象，无需参数
     * @return 清理的规则数量
     */
    @OperationLog(module = "IP访问控制", type = OperationType.DELETE, description = "清理过期IP访问控制规则")
    @Operation(operationId = "cleanupExpiredRules", summary = "清理过期规则", description = "自动清理所有已过期的临时黑名单规则，返回清理数量")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "清理成功，返回清理的规则数量",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "服务器内部错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> cleanupExpiredRules(ServerRequest request) {
        return ipAccessControlService
                .cleanupExpiredRules()
                .flatMap(count -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(count)))
                .onErrorResume(this::handleError);
    }

    /**
     * 检查IP访问权限
     *
     * <p>实时检查指定IP地址是否允许访问系统，根据白名单、黑名单和默认策略综合判断。
     *
     * <p><b>判断逻辑：</b>
     * <ol>
     *   <li>首先检查黑名单：如果IP在黑名单中且规则启用，直接返回false（拒绝访问）</li>
     *   <li>然后检查白名单：如果IP在白名单中且规则启用，返回true（允许访问）</li>
     *   <li>最后应用默认策略：如果既不在黑名单也不在白名单，根据系统配置决定（通常允许）</li>
     * </ol>
     *
     * <p><b>参数说明：</b>
     * <ul>
     *   <li><b>ipAddress</b>：要检查的IP地址</li>
     *   <li><b>tenantId</b>：租户ID，确保多租户隔离</li>
     * </ul>
     *
     * <p><b>返回值：</b>
     * <ul>
     *   <li><b>true</b>：允许访问</li>
     *   <li><b>false</b>：拒绝访问</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>API网关在请求转发前检查IP权限</li>
     *   <li>登录接口验证IP是否在允许范围内</li>
     *   <li>管理后台测试IP规则是否生效</li>
     * </ul>
     *
     * <p><b>性能优化：</b>
     * <ul>
     *   <li>规则缓存在Redis中，检查性能极高</li>
     *   <li>支持CIDR段匹配，无需逐个IP配置</li>
     * </ul>
     *
     * @param request 服务器请求对象，查询参数包含ipAddress、tenantId
     * @return 布尔值，true表示允许访问，false表示拒绝访问
     */
    @OperationLog(module = "IP访问控制", type = OperationType.SELECT, description = "检查IP访问权限")
    @Operation(operationId = "checkAccess", summary = "检查IP访问权限", description = "实时检查指定IP地址是否允许访问系统，根据白名单、黑名单综合判断")
    @Parameter(name = "ipAddress", description = "要检查的IP地址", required = true, example = "192.168.1.100")
    @Parameter(name = "tenantId", description = "租户ID", required = true, example = "tenant_001")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "检查成功，返回布尔值（true=允许访问，false=拒绝访问）",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "参数错误 - IP地址格式不正确",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> checkAccess(ServerRequest request) {
        String ipAddress = request.queryParam("ipAddress")
                .orElseThrow(() -> new IllegalArgumentException("ipAddress is required"));
        String tenantId =
                request.queryParam("tenantId").orElseThrow(() -> new IllegalArgumentException("tenantId is required"));

        return ipAccessControlService
                .isAccessAllowed(ipAddress, tenantId)
                .flatMap(allowed -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(allowed)))
                .onErrorResume(this::handleError);
    }

    private Mono<ServerResponse> handleError(Throwable error) {
        log.error("IP访问控制操作失败", error);
        return ServerResponse.status(500)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Result.error(error.getMessage()));
    }
}
