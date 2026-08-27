package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.HttpStatusUtil;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.interfaces.util.MessageUtils;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.security.Principal;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 请求处理器抽象基类
 *
 * <p>采用模板方法模式，为所有接口层 Handler 提供公共能力支持。
 * 消除多个 Handler 中重复的 {@code currentOpenId}、数字解析和响应构建方法定义，
 * 统一对请求参数进行安全处理，并提供国际化的错误/成功响应构建能力。
 *
 * <h2>提供的公共方法</h2>
 * <ul>
 *   <li>{@link #currentOpenId(ServerRequest)} — 从请求上下文中获取当前认证用户的 openId</li>
 *   <li>{@link #currentUserId(ServerRequest, com.apex.scaffold.application.service.UserService)} — 从认证上下文中解析当前登录用户的业务 ID</li>
 *   <li>{@link #safeParseLong(String)} — 安全解析 Long 类型路径参数，异常时返回 null</li>
 *   <li>{@link #safeParseInt(String, int)} — 安全解析 Integer 类型查询参数</li>
 *   <li>{@link #buildOk(Result)} — 构建 200 成功响应</li>
 *   <li>{@link #buildError(ResultCode, String)} — 构建带国际化的错误响应</li>
 *   <li>{@link #buildError(Integer, String, String, Object...)} — 构建带国际化的自定义错误响应</li>
 * </ul>
 *
 * <h2>模板方法</h2>
 * <ul>
 *   <li>{@link #handleAuthenticated(ServerRequest, Function)} — 认证用户 + 执行业务逻辑 + 包装成功响应</li>
 *   <li>{@link #handleAuthenticatedWithBody(ServerRequest, Class, BiFunction)} — 认证用户 + 解析请求体 + 执行业务逻辑 + 包装成功响应</li>
 *   <li>{@link #validateAndThrow(Object)} — Jakarta Bean Validation 校验，校验失败时抛出 BusinessException</li>
 *   <li>{@link #currentUserId(ServerRequest, com.apex.scaffold.application.service.UserService)} — 从认证上下文中解析当前登录用户的业务 ID</li>
 * </ul>
 *
 * <h2>子类约定</h2>
 * <ul>
 *   <li>所有 Handler 应继承此类，在构造函数中通过 {@code super(messageUtils)} 或 {@code super(messageUtils, validator)} 传递 {@link MessageUtils} 和可选的 {@link Validator}</li>
 *   <li>Handler 方法中统一使用 {@code buildOk()} / {@code buildError()} 构建响应</li>
 *   <li>路径参数解析统一使用 {@code safeParseLong} 避免 NumberFormatException</li>
 * </ul>
 *
 * <h2>国际化实现说明</h2>
 * <p>
 * {@code messageUtils} 通过构造器注入，所有继承此类的 Handler 必须在构造函数
 * 中通过 {@code super(messageUtils)} 传递实例，遵循 Spring 推荐的构造器注入最佳实践。
 * </p>
 *
 * @author Apex Team
 * @version 2.1.0
 * @since 1.0.0
 * @see com.apex.scaffold.interfaces.util.MessageUtils
 * @see com.apex.scaffold.application.service
 */
public abstract class BaseHandler {

    /** 国际化消息工具，用于根据客户端语言返回对应文本 */
    protected final MessageUtils messageUtils;

    /** Jakarta Bean Validation 校验器，用于对请求体进行声明式约束校验 */
    protected final Validator validator;

    /**
     * 构造方法，注入 MessageUtils
     *
     * <p>所有继承此类的 Handler 必须在构造函数中通过 {@code super(messageUtils)}
     * 传递 {@link MessageUtils} 实例。使用此构造方法时，{@link #validator} 将为
     * {@code null}，调用 {@link #validateAndThrow(Object)} 前需确保通过带
     * {@link Validator} 参数的构造方法初始化。
     *
     * @param messageUtils 国际化消息工具实例，由 Spring 容器注入
     */
    protected BaseHandler(MessageUtils messageUtils) {
        this.messageUtils = messageUtils;
        this.validator = null;
    }

    /**
     * 构造方法，注入 MessageUtils 和 Validator
     *
     * <p>当 Handler 需要进行 Jakarta Bean Validation 校验时使用此构造方法。
     * 通过 {@code super(messageUtils, validator)} 同时传递两个依赖。
     *
     * @param messageUtils 国际化消息工具实例，由 Spring 容器注入
     * @param validator    Jakarta Bean Validation 校验器实例，由 Spring 容器注入
     */
    protected BaseHandler(MessageUtils messageUtils, Validator validator) {
        this.messageUtils = messageUtils;
        this.validator = validator;
    }

    /**
     * 获取当前认证用户的 openId
     *
     * <p>从 {@link ServerRequest} 的 {@link Principal} 中提取当前登录用户的
     * 唯一标识（微信小程序场景下为 openId）。该值在
     * {@link com.apex.scaffold.interfaces.security.JwtAuthenticationFilter}
     * 中完成认证后设置到请求上下文中。
     *
     * <h3>使用示例</h3>
     * <pre>{@code * public Mono<ServerResponse> someMethod(ServerRequest request) {
     *     String traceId = TraceIdUtil.getTraceId();
     *     return currentOpenId(request)
     *         .flatMap(openId -> someService.doSomething(openId, ...))
     *         .map(data -> Result.success(data, traceId))
     *         .flatMap(this::buildOk);
     * }
     * }</pre>
     *
     * @param request 当前 HTTP 请求对象，持有认证上下文
     * @return 当前用户的 openId 的 Mono 包装
     */
    protected Mono<String> currentOpenId(ServerRequest request) {
        return request.principal().map(Principal::getName);
    }

    /**
     * 安全解析 Long 类型路径参数
     *
     * <p>对 {@link ServerRequest#pathVariable} 获取的字符串值进行安全转换。
     * 当参数为非数字格式时返回 null 而不是抛出 {@link NumberFormatException}，
     * 避免直接使用 {@link Long#parseLong(String)} 在参数不合法时导致 HTTP 500 错误。
     *
     * <h3>使用示例</h3>
     * <pre>{@code * Long chatId = safeParseLong(request.pathVariable("chatId"));
     * if (chatId == null) {
     *     return buildError(ResultCode.PARAM_ERROR.getCode(), "param.chat_id_invalid", traceId);
     * }
     * }</pre>
     *
     * @param value 路径参数字符串
     * @return 成功则返回对应的 Long 值，参数为空或非数值时返回 null
     */
    protected Long safeParseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 安全解析 Integer 类型请求参数
     *
     * <p>对请求中的数值查询参数进行安全转换。当参数为空或非数字格式时，
     * 返回指定的默认值而不是抛出异常。支持 pageNum=0 表示第一页的场景，
     * 允许返回 0 及正整数值。
     *
     * <h3>使用示例</h3>
     * <pre>{@code * int pageNum = safeParseInt(request.queryParam("pageNum").orElse(null), 1);
     * int pageSize = safeParseInt(request.queryParam("pageSize").orElse(null), 20);
     * }</pre>
     *
     * @param value        参数字符串，允许为 null
     * @param defaultValue 解析失败或参数为空时返回的默认值
     * @return 成功则返回对应的 Integer 值，失败则返回 defaultValue
     */
    protected int safeParseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int result = Integer.parseInt(value.trim());
            return result >= 0 ? result : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 构建 200 成功响应
     *
     * <p>将已有的 {@link Result} 对象包装为 HTTP 200 JSON 响应。
     * Content-Type 设置为 {@code application/json}。
     *
     * <h3>使用示例</h3>
     * <pre>{@code * return someService.getData(openId)
     *     .map(data -> Result.success(data, traceId))
     *     .flatMap(this::buildOk);
     * }</pre>
     *
     * @param result 统一响应结果对象
     * @return HTTP 200 响应的 Mono，Content-Type 为 application/json
     */
    protected Mono<ServerResponse> buildOk(Result<?> result) {
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(result);
    }

    /**
     * 构建携带当前 traceId 的统一成功响应（模板方法）。
     *
     * <p>业务数据 → {@code Result.success(data, traceId)} → HTTP 200 JSON 响应 的
     * 完整包装链。这是所有 Handler 成功出口的<b>唯一标准入口</b>，用于消除各
     * Handler 中重复的私有 {@code ok(T)} 方法（历史版本中 CaptchaHandler、
     * DictHandler、ConfigHandler、JobHandler、MenuHandler、LoginLogHandler、
     * AdminUserManagementHandler 各维护一份平行实现，均已收敛至本方法）。</p>
     *
     * <p>区别于 {@link #buildOk(Result)}：本方法自动从 {@link TraceIdUtil} 获取
     * traceId 并完成 {@link Result#success(Object, String)} 包装，调用方无需手动
     * 组装 {@link Result}。</p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code * public Mono<ServerResponse> list(ServerRequest request) {
     *     return someService.list().flatMap(this::buildSuccess);
     * }
     * }</pre>
     *
     * @param <T>  业务数据类型，将被包装到 {@link Result#data}
     * @param data 业务数据，可为 {@code null}
     * @return 携带当前 traceId 的 HTTP 200 JSON 响应
     * @since 2.2.0
     */
    protected <T> Mono<ServerResponse> buildSuccess(T data) {
        return buildOk(Result.success(data, TraceIdUtil.getTraceId()));
    }

    /**
     * 解析请求体并执行 Jakarta Bean Validation 校验（模板方法）。
     *
     * <p>封装了 POST/PUT/PATCH 类 Handler 中最重复的请求体处理链：
     * {@code bodyToMono → switchIfEmpty(请求体不能为空) → validateAndThrow}。
     * 用于消除各 Handler 中逐字重复的 6+ 处
     * {@code request.bodyToMono(X).switchIfEmpty(...).flatMap(this::validateAndThrow)}
     * 样板代码。</p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code * public Mono<ServerResponse> create(ServerRequest request) {
     *     return parseBody(request, DictTypeRequest.class)
     *         .flatMap(dictService::createType)
     *         .flatMap(this::buildSuccess);
     * }
     * }</pre>
     *
     * @param <T>       请求体类型，携带 Jakarta Bean Validation 约束注解
     * @param request   HTTP 请求对象
     * @param bodyClass 请求体的 Java 类型，用于 {@link ServerRequest#bodyToMono(Class)} 反序列化
     * @return 校验通过时返回含请求体的 Mono；请求体为空或校验失败时返回 error Mono
     * @throws IllegalStateException 如果 {@link Validator} 未注入（使用无 Validator 的构造方法初始化时）
     * @since 2.2.0
     */
    protected <T> Mono<T> parseBody(ServerRequest request, Class<T> bodyClass) {
        return request.bodyToMono(bodyClass)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(this::validateAndThrow);
    }

    /**
     * 解析并校验路径参数 {@code id}（模板方法）。
     *
     * <p>封装了各 Handler 中重复的 {@code safeParseLong(pathVariable("id")) + 正数校验}
     * 模式：路径参数 {@code id} 必须为大于 0 的整数，否则抛出
     * {@link BusinessException}（错误码 {@link ResultCode#PARAM_ERROR}）。
     * 用于消除 DictHandler/ConfigHandler/JobHandler 中逐字重复的
     * {@code typeId()/configId()/jobId()} 私有辅助方法。</p>
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 大于 0 的资源 ID
     * @throws BusinessException 当参数缺失、非数字或不大于 0 时抛出
     * @since 2.2.0
     */
    protected long requirePositiveId(ServerRequest request) {
        Long id = safeParseLong(request.pathVariable("id"));
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "id格式错误");
        }
        return id;
    }

    /**
     * 根据错误码构建国际化错误响应
     *
     * <p>通过 {@link ResultCode#getMessageKey()} 获取消息键，
     * 使用 {@link MessageUtils} 从 Reactor Context 中读取 Locale 并解析对应语言的消息文本。
     * HTTP 状态码根据业务错误码自动映射。
     *
     * <h3>状态码映射规则</h3>
     * <ul>
     *   <li>401 → 401 Unauthorized</li>
     *   <li>403 → 403 Forbidden</li>
     *   <li>404 → 404 Not Found</li>
     *   <li>400~499 → 400 Bad Request</li>
     *   <li>500+ → 500 Internal Server Error</li>
     *   <li>其他 → 400 Bad Request</li>
     * </ul>
     *
     * @param resultCode 错误码枚举，包含 code 和 messageKey
     * @param traceId    链路追踪 ID
     * @return 包含国际化错误消息的 HTTP 响应的 Mono
     */
    protected Mono<ServerResponse> buildError(ResultCode resultCode, String traceId) {
        return messageUtils
                .get(resultCode.getMessageKey())
                .map(msg -> Result.error(resultCode.getCode(), msg, traceId))
                .flatMap(result -> ServerResponse.status(HttpStatusUtil.fromBusinessCode(resultCode.getCode()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result));
    }

    /**
     * 根据自定义消息键构建国际化错误响应
     *
     * <p>适用于不在 {@link ResultCode} 枚举中定义的动态错误消息。
     * 消息键对应 messages_*.properties 中的 key。
     *
     * <h3>使用示例</h3>
     * <pre>{@code * if (openId == null) {
     *     return buildError(ResultCode.PARAM_ERROR.getCode(), "param.open_id_not_null", traceId);
     * }
     * }</pre>
     *
     * @param code       业务错误码
     * @param messageKey 国际化消息键
     * @param traceId    链路追踪 ID
     * @param args       消息模板参数（可选），按顺序替换 {0}、{1} 等占位符
     * @return 包含国际化错误消息的 HTTP 响应的 Mono
     */
    protected Mono<ServerResponse> buildError(Integer code, String messageKey, String traceId, Object... args) {
        return messageUtils
                .get(messageKey, args)
                .map(msg -> Result.error(code, msg, traceId))
                .flatMap(result -> ServerResponse.status(HttpStatusUtil.fromBusinessCode(code))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result));
    }

    /**
     * 根据错误码确定 HTTP 状态码
     *
     * <p>将业务错误码映射为 HTTP 协议状态码，确保异常响应符合语义。
     *
     * @param code 业务错误码
     * @return 对应的 HTTP 状态码
     */
    private HttpStatus httpStatusFor(Integer code) {
        return HttpStatusUtil.fromBusinessCode(code);
    }

    // ==================== 模板方法 ====================

    /**
     * 模板方法：认证用户 + 执行业务逻辑 + 包装成功响应
     *
     * <p>封装了 Handler 中最常见的处理流程：先从请求上下文获取已认证用户的 openId，
     * 再将 openId 传递给业务方法执行核心逻辑，最后将业务结果包装为统一 JSON 响应。
     * 该方法适用于仅需要用户身份即可完成操作的 GET/HEAD 类请求。
     *
     * <p>处理链概览：
     * <ol>
     *   <li>调用 {@link #currentOpenId(ServerRequest)} 获取已认证用户的微信 openId</li>
     *   <li>将 openId 传递给 {@code serviceCall} 函数执行业务逻辑</li>
     *   <li>将业务结果包装为 {@code Result.success(data, traceId)} 标准 JSON 响应</li>
     *   <li>通过 {@link #buildOk(Result)} 构建 HTTP 200 响应</li>
     * </ol>
     *
     * <p>若请求上下文无有效认证信息，则 {@link #currentOpenId(ServerRequest)} 将
     * 发出空信号导致下游中断；层调用方应提前通过 Filter 确保认证已就绪。
     *
     * <p>错误处理不在本方法范围内，调用方应在 {@code serviceCall} 中自行处理业务异常。
     *
     * <h3>使用示例</h3>
     * <pre>{@code * public Mono<ServerResponse> getMyProfile(ServerRequest request) {
     *     return handleAuthenticated(request, userService::getUserProfile);
     * }
     * }</pre>
     *
     * @param <T>         业务方法返回类型，将被包装到 {@link Result#data} 中
     * @param request     HTTP 请求对象，用于提取认证上下文
     * @param serviceCall 接收 openId 并返回业务结果的异步函数，输入为当前用户的 openId
     * @return 标准 JSON 响应的 Mono，携带 traceId 和业务数据
     * @since 2.1.0
     */
    protected <T> Mono<ServerResponse> handleAuthenticated(
            ServerRequest request, Function<String, Mono<T>> serviceCall) {
        String traceId = TraceIdUtil.getTraceId();
        return currentOpenId(request)
                .flatMap(serviceCall)
                .map(data -> Result.success(data, traceId))
                .flatMap(this::buildOk);
    }

    /**
     * 模板方法：认证用户 + 解析请求体 + 执行业务逻辑 + 包装成功响应
     *
     * <p>封装了 POST/PUT/PATCH 类 Handler 中最常见的处理流程：先从请求上下文获取
     * 已认证用户的 openId，再解析 JSON 请求体为指定类型，然后将两者传递给业务方法
     * 执行核心逻辑，最后将业务结果包装为统一 JSON 响应。
     *
     * <p>处理链概览：
     * <ol>
     *   <li>调用 {@link #currentOpenId(ServerRequest)} 获取已认证用户的微信 openId</li>
     *   <li>调用 {@link ServerRequest#bodyToMono(Class)} 反序列化 JSON 请求体</li>
     *   <li>将 (openId, body) 二元组传递给 {@code serviceCall} 执行业务逻辑</li>
     *   <li>将业务结果包装为 {@code Result.success(data, traceId)} 标准 JSON 响应</li>
     *   <li>通过 {@link #buildOk(Result)} 构建 HTTP 200 响应</li>
     * </ol>
     *
     * <p>适用于需要用户身份和请求体数据的 POST/PUT/PATCH 请求。
     * 建议在调用此方法前先调用 {@link #validateAndThrow(Object)} 对请求体进行
     * Jakarta Bean Validation 校验，或由 Spring WebFlux 自动执行参数校验。
     *
     * <h3>使用示例</h3>
     * <pre>{@code * public Mono<ServerResponse> updateProfile(ServerRequest request) {
     *     return request.bodyToMono(UpdateProfileRequest.class)
     *         .flatMap(this::validateAndThrow)
     *         .flatMap(body -> handleAuthenticatedWithBody(request, UpdateProfileRequest.class,
     *             (openId, req) -> userService.updateProfile(openId, req)));
     * }
     * }</pre>
     *
     * @param <T>         业务方法返回类型，将被包装到 {@link Result#data} 中
     * @param <R>         请求体类型，由 {@code bodyClass} 参数指定
     * @param request     HTTP 请求对象，用于提取认证上下文和请求体
     * @param bodyClass   请求体的 Java 类型，用于 {@link ServerRequest#bodyToMono(Class)} 反序列化
     * @param serviceCall 接收 (openId, body) 二元组并返回业务结果的异步函数
     * @return 标准 JSON 响应的 Mono，携带 traceId 和业务数据
     * @since 2.1.0
     */
    protected <T, R> Mono<ServerResponse> handleAuthenticatedWithBody(
            ServerRequest request, Class<R> bodyClass, BiFunction<String, R, Mono<T>> serviceCall) {
        String traceId = TraceIdUtil.getTraceId();
        return currentOpenId(request)
                .flatMap(openId -> request.bodyToMono(bodyClass).flatMap(body -> serviceCall.apply(openId, body)))
                .map(data -> Result.success(data, traceId))
                .flatMap(this::buildOk);
    }

    /**
     * 执行 Jakarta Bean Validation 校验
     *
     * <p>对传入对象执行标准的 Jakarta Bean Validation（JSR 380）约束校验。
     * 校验通过时将原对象包装为 {@link Mono#just(Object)} 返回，供下游响应式链继续处理；
     * 校验失败时聚合所有约束违反信息，以逗号分隔拼接为一组友好错误提示，
     * 通过 {@link Mono#error(Throwable)} 传播 {@link BusinessException}（错误码
     * {@link ResultCode#PARAM_ERROR}）。
     *
     * <p>该方法设计为响应式链中的一个操作符，典型用法是在
     * {@code request.bodyToMono(BodyClass.class).flatMap(this::validateAndThrow)}
     * 中串联使用，实现请求体反序列化后立即校验。
     *
     * <p><b>前置条件</b>：必须通过 {@link #BaseHandler(MessageUtils, Validator)}
     * 构造方法注入 {@link Validator}，否则调用将抛出 {@link IllegalStateException}。
     * 使用仅接受 {@link MessageUtils} 的构造方法初始化的 Handler 不可直接调用此法。
     *
     * <h3>校验失败行为</h3>
     * <ul>
     *   <li>所有约束违反信息按自然顺序以英文逗号分隔拼接</li>
     *   <li>抛出 {@code BusinessException(ResultCode.PARAM_ERROR, "字段xxx不能为空, 字段yyy格式不正确")}</li>
     *   <li>由 WebFlux 全局异常处理器将异常转换为 HTTP 400 错误响应</li>
     * </ul>
     *
     * <h3>使用示例</h3>
     * <pre>{@code * public Mono<ServerResponse> submitForm(ServerRequest request) {
     *     return request.bodyToMono(SubmitFormRequest.class)
     *         .flatMap(this::validateAndThrow)
     *         .flatMap(body -> handleAuthenticatedWithBody(request, SubmitFormRequest.class,
     *             (openId, req) -> formService.submit(openId, req)));
     * }
     * }</pre>
     *
     * @param <T>  待校验类型，期望携带 Jakarta Bean Validation 注解（如
     *             {@code @NotBlank}、{@code @NotNull}、{@code @Size} 等）
     * @param body 待校验的对象实例，通常由请求体反序列化后获得
     * @return 校验通过时返回含原 body 的 Mono，失败时返回 error Mono（携带 BusinessException）
     * @throws IllegalStateException 如果 {@link Validator} 未注入（使用错误构造方法初始化时）
     * @since 2.1.0
     */
    protected <T> Mono<T> validateAndThrow(T body) {
        if (validator == null) {
            throw new IllegalStateException("Validator 未注入到 BaseHandler，请在子类构造函数中传递 Validator");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(body);
        if (violations.isEmpty()) {
            return Mono.just(body);
        }
        String message =
                violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, message));
    }

    /**
     * 从认证上下文中解析当前登录用户的业务 ID
     *
     * <p>将认证层的 openId 转换为业务层的用户主键 ID。该方法封装了以下两步操作：
     * <ol>
     *   <li>从 {@link ServerRequest} 的 {@link Principal} 中获取 openId</li>
     *   <li>通过 {@link com.apex.scaffold.application.service.UserService#getByOpenId(String)}
     *       查询对应的 {@link com.apex.scaffold.domain.model.entity.UserEntity}，提取其
     *       {@code id} 字段</li>
     * </ol>
     *
     * <p>若当前请求未经认证（无 Principal），则 {@link #currentOpenId(ServerRequest)}
     * 返回空 Mono，不会进入后续流程。若 openId 对应的用户不存在，则通过
     * {@link Mono#switchIfEmpty(Mono)} 降级为 {@link ResultCode#UNAUTHORIZED} 错误。
     *
     * <p>该方法适用于需要以用户主键 ID 为参数的场景，例如数据库查询、对局创建、
     * 算力扣减等。相比直接使用 openId，业务 ID 更加稳定且查询效率更高。
     *
     * <h3>使用示例</h3>
     * <pre>{@code * public Mono<ServerResponse> startBattle(ServerRequest request) {
     *     return currentUserId(request, userService)
     *         .flatMap(userId -> battleService.startBattle(userId))
     *         .map(data -> Result.success(data, TraceIdUtil.getTraceId()))
     *         .flatMap(this::buildOk);
     * }
     * }</pre>
     *
     * @param request     HTTP 请求对象，用于提取认证上下文
     * @param userService 用户服务，用于根据 openId 查询用户实体，由调用方通过构造器注入后传入
     * @return 当前用户主键 ID 的 Mono，用户不存在时发出 {@link ResultCode#UNAUTHORIZED} 错误信号
     * @since 2.1.0
     */
    protected Mono<Long> currentUserId(
            ServerRequest request, com.apex.scaffold.application.service.UserService userService) {
        return currentOpenId(request)
                .flatMap(userService::getByOpenId)
                .map(com.apex.scaffold.domain.model.entity.UserEntity::getId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "未登录")));
    }
}
