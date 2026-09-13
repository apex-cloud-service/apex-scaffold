package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.assembler.UserAssembler;
import com.apex.scaffold.application.dto.AccountDeletionRequest;
import com.apex.scaffold.application.dto.PageRequest;
import com.apex.scaffold.application.dto.UserDTO;
import com.apex.scaffold.application.service.AccountDeletionService;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.interfaces.util.MessageUtils;
import com.apex.scaffold.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 用户处理器（函数式端点）。
 *
 * <p>提供用户信息管理的 RESTful API，基于 Spring WebFlux 函数式编程模型实现。
 * 包含当前用户信息查询、按 ID 查询、分页列表查询，以及账号注销（短信二次验证）
 * 等功能。项目 API 统一通过公开白名单访问，与当前用户相关的操作使用请求上下文中
 * 可用的用户身份，并集成操作日志记录
 * （{@link com.apex.scaffold.common.annotation.OperationLog}）。</p>
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>获取当前登录用户信息</li>
 *   <li>根据用户 ID 查询用户信息</li>
 *   <li>分页查询用户列表</li>
 *   <li>发送账号注销验证码并注销当前账号</li>
 * </ul>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /api/v1/user/info - 获取当前用户信息</li>
 *   <li>GET /api/v1/user/{userId} - 根据 ID 获取用户信息</li>
 *   <li>POST /api/v1/user/list - 分页查询用户列表</li>
 *   <li>POST /api/v1/user/me/deletion-code - 发送账号注销验证码</li>
 *   <li>DELETE /api/v1/user/me - 注销当前账号</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see BaseHandler
 * @see UserService
 * @see UserAssembler
 */
@Slf4j
@Component
@Tag(name = "用户管理", description = "用户信息管理相关接口")
public class UserHandler extends BaseHandler {

    private final UserService userService;
    private final UserAssembler userAssembler;
    private final RequestHandlerUtil requestHandlerUtil;
    private final AccountDeletionService accountDeletionService;

    /**
     * 构造用户处理器
     *
     * @param userService 用户服务
     * @param userAssembler 用户装配器
     * @param requestHandlerUtil 请求处理工具类，用于统一解析请求体和校验参数
     * @param messageUtils 国际化消息工具
     */
    public UserHandler(
            UserService userService,
            UserAssembler userAssembler,
            RequestHandlerUtil requestHandlerUtil,
            MessageUtils messageUtils,
            AccountDeletionService accountDeletionService) {
        super(messageUtils);
        this.userService = userService;
        this.userAssembler = userAssembler;
        this.requestHandlerUtil = requestHandlerUtil;
        this.accountDeletionService = accountDeletionService;
    }

    /**
     * 获取当前用户信息
     *
     * <p>
     * 从请求中解析当前登录用户的 OpenID，通过 OpenID 查询用户详细信息。
     * 如果用户不存在则返回 {@link ResultCode#USER_NOT_FOUND} 错误。
     *
     * @param request 服务器请求对象，包含用户的 JWT 认证信息
     * @return 包含用户详情的响应对象，成功时返回 {@link UserDTO}
     * @throws com.apex.scaffold.common.exception.BusinessException 当用户不存在时抛出
     */
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的详细信息")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "获取成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class))),
        @ApiResponse(
                responseCode = "401",
                description = "未登录",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(module = "用户管理", type = OperationLog.OperationType.SELECT, description = "获取当前用户信息")
    public Mono<ServerResponse> getCurrentUserInfo(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        return currentOpenId(request)
                .flatMap(userService::getByOpenId)
                .map(userAssembler::toDTO)
                .flatMap(this::buildSuccess)
                .switchIfEmpty(buildError(ResultCode.USER_NOT_FOUND, traceId));
    }

    /**
     * 根据ID获取用户信息
     *
     * <p>
     * 从请求路径参数中获取用户 ID，通过 ID 查询指定用户的详细信息。
     * ID 参数使用 {@link BaseHandler#safeParseLong} 安全解析，避免 NumberFormatException。
     * 该项目 API 已加入统一白名单，不执行 Token 权限校验。
     *
     * @param request 服务器请求对象，包含路径参数 {@code userId}
     * @return 包含用户详情的响应对象，成功时返回 {@link UserDTO}
     */
    @Parameters({@Parameter(name = "userId", in = ParameterIn.PATH, required = true, description = "用户ID")})
    @Operation(summary = "根据ID获取用户信息", description = "获取指定用户的详细信息")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "获取成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserDTO.class))),
        @ApiResponse(
                responseCode = "404",
                description = "用户不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(module = "用户管理", type = OperationLog.OperationType.SELECT, description = "根据 ID 获取用户信息")
    public Mono<ServerResponse> getUserById(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        Long userId = safeParseLong(request.pathVariable("userId"));
        if (userId == null) {
            return buildError(ResultCode.PARAM_ERROR, traceId);
        }
        return userService
                .getById(userId)
                .map(userAssembler::toDTO)
                .flatMap(this::buildSuccess)
                .switchIfEmpty(buildError(ResultCode.USER_NOT_FOUND, traceId));
    }

    /**
     * 分页查询用户列表
     *
     * <p>
     * 从请求体中解析分页参数，通过 {@link RequestHandlerUtil#handleRequestBody}
     * 统一进行参数校验，执行分页查询并返回用户列表数据。
     * 需要 {@code user:list} 权限。
     *
     * <h3>请求参数</h3>
     * <ul>
     *   <li>{@code pageNum} - 页码，从 1 开始</li>
     *   <li>{@code pageSize} - 每页记录数</li>
     * </ul>
     *
     * @param request 服务器请求对象，包含 JSON 格式的分页查询参数
     * @return 包含分页查询结果的响应对象，成功时返回 {@code PageResponse<UserDTO>}
     */
    @Operation(summary = "分页查询用户列表", description = "分页查询用户列表，无需携带 Token")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "分页参数不合法",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(module = "用户管理", type = OperationLog.OperationType.SELECT, description = "分页查询用户列表")
    public Mono<ServerResponse> listUsers(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        return requestHandlerUtil.handleRequestBody(request, PageRequest.class).flatMap(body -> userService
                .findPage(body.getPageNum(), body.getPageSize(), body.getOrderBy(), body.getIsAsc())
                .map(data -> {
                    PageResponse<UserDTO> response = new PageResponse<>();
                    response.setList(userAssembler.toDTOList(data.getList()));
                    response.setTotal(data.getTotal());
                    response.setPageNum(data.getPageNum());
                    response.setPageSize(data.getPageSize());
                    response.setPages(data.getPages());
                    response.setHasNext(data.getHasNext());
                    return response;
                })
                .map(data -> Result.success(data, traceId))
                .flatMap(this::buildOk));
    }

    /**
     * 向当前账号绑定手机号发送注销二次验证短信。
     *
     * <p>手机号从认证身份对应的账号读取，客户端不能提交其他手机号。</p>
     *
     * @param request 服务器请求
     * @return 发送成功响应
     */
    @Operation(summary = "发送账号注销验证码", description = "向当前账号已绑定手机号发送注销二次验证短信，不接受客户端传入手机号")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "验证码已发送"),
        @ApiResponse(responseCode = "400", description = "账号未绑定手机号"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "429", description = "发送过于频繁")
    })
    @OperationLog(module = "用户管理", type = OperationLog.OperationType.UPDATE, description = "发送账号注销验证码")
    public Mono<ServerResponse> sendAccountDeletionCode(ServerRequest request) {
        if (accountDeletionService == null) {
            return Mono.error(new IllegalStateException("账号注销服务未配置"));
        }
        String traceId = TraceIdUtil.getTraceId();
        return currentOpenId(request)
                .flatMap(accountDeletionService::sendDeletionCode)
                .then(buildSuccess(null));
    }

    /**
     * 注销当前账号。
     *
     * <p>校验短信后立即让账号失效并返回 202；照片对象、偏爱关系和算力流水等
     * 后续清理由后台任务处理。聊天消息、举报工单/证据及 Elo 历史不在清理范围。</p>
     *
     * @param request 服务器请求
     * @return 202 受理响应
     */
    @Operation(summary = "注销当前账号", description = "短信二次验证通过后立即失效账号，返回异步清理任务标识")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "账号已失效，清理任务已受理"),
        @ApiResponse(responseCode = "400", description = "验证码错误或请求参数不合法"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "409", description = "会员权益尚未过期或注销已完成"),
        @ApiResponse(responseCode = "429", description = "请求过于频繁")
    })
    @OperationLog(module = "用户管理", type = OperationLog.OperationType.DELETE, description = "注销当前账号")
    public Mono<ServerResponse> deleteCurrentUser(ServerRequest request) {
        if (accountDeletionService == null) {
            return Mono.error(new IllegalStateException("账号注销服务未配置"));
        }
        String traceId = TraceIdUtil.getTraceId();
        String requestId = request.headers().firstHeader("Idempotency-Key");
        return requestHandlerUtil
                .handleRequestBody(request, AccountDeletionRequest.class)
                .flatMap(body -> currentOpenId(request)
                        .flatMap(openId -> accountDeletionService.deleteCurrentUser(
                                openId,
                                body.getCode(),
                                requestId,
                                requestHandlerUtil.extractClientIp(request),
                                traceId)))
                .map(result -> Result.success(result, traceId))
                .flatMap(result -> ServerResponse.status(HttpStatus.ACCEPTED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result));
    }
}
