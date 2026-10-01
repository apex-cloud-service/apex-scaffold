package com.apex.scaffold.interfaces.router;

import com.apex.scaffold.common.result.Result;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 兜底路由函数：当请求路径存在但 HTTP 方法不匹配时返回 405 Method Not Allowed。
 *
 * <p>Spring WebFlux 函数式路由（{@link RouterFunction}）在 HTTP 方法不匹配时
 * 默认返回 404，因为谓词（{@code RequestPredicate}）包含方法检查。
 * 本路由作为最低优先级的兜底 RouterFunction，在所有其他路由匹配失败后执行，
 * 检查当前请求路径是否注册了其他 HTTP 方法的处理器。若路径存在但方法不匹配则返回 405。</p>
 *
 * <h2>典型场景</h2>
 * <ul>
 *   <li>{@code GET /api/v1/auth/login} → 405（仅注册了 POST）</li>
 *   <li>{@code GET /api/v1/auth/register} → 405（仅注册了 POST）</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.0.0
 */
@Component
public class MethodNotAllowedRouter implements RouterFunction<ServerResponse>, Ordered {

    private static final String API_PATH_PREFIX = "/api/";

    private final List<RouterFunction<ServerResponse>> delegates;

    /**
     * 用已注册的路由委托列表构造 405 兜底路由器。
     *
     * @param delegates 已注册的路由函数委托列表
     */
    public MethodNotAllowedRouter(List<RouterFunction<ServerResponse>> delegates) {
        this.delegates = delegates;
    }

    /**
     * {@inheritDoc}
     *
     * @param request 待匹配的请求
     * @return 探测到可处理该请求的处理器；无匹配时为空
     */
    @Override
    @NonNull
    public Mono<HandlerFunction<ServerResponse>> route(@NonNull ServerRequest request) {
        String path = request.path();

        if (!path.startsWith(API_PATH_PREFIX)) {
            return Mono.empty();
        }

        return anyDelegateMatches(request)
                .filter(matched -> !matched)
                .flatMap(notHandled -> tryCandidateMethods(request));
    }

    /**
     * 用常见业务 HTTP 方法逐一探测，并把全部已注册方法写入 Allow 响应头。
     */
    private Mono<HandlerFunction<ServerResponse>> tryCandidateMethods(ServerRequest request) {
        HttpMethod originalMethod = request.method();
        return Flux.fromIterable(CANDIDATE_METHODS)
                .filter(method -> method != originalMethod)
                .concatMap(candidate -> {
                    ServerRequest mutated = buildMutatedRequest(request, candidate);
                    return anyDelegateMatches(mutated)
                            .filter(matched -> matched)
                            .map(matched -> candidate);
                })
                .collect(LinkedHashSet<HttpMethod>::new, Set::add)
                .filter(allowedMethods -> !allowedMethods.isEmpty())
                .flatMap(this::buildMethodNotAllowedHandler);
    }

    /**
     * 非阻塞检查任一委托 RouterFunction 是否匹配给定请求。
     *
     * <p>使用 {@code concatMap} 顺序探测以避免多个 delegate 并发
     * 向同一 exchange attributes 写入路径变量时发生冲突。
     * 探测结束后恢复原始路径变量，避免污染调用方的请求。</p>
     */
    private Mono<Boolean> anyDelegateMatches(ServerRequest request) {
        Map<String, String> originalVariables = originalPathVariables(request.exchange());
        return Flux.fromIterable(delegates)
                .filter(delegate -> delegate != this)
                .concatMap(delegate -> probeDelegate(delegate, request))
                .any(matched -> matched)
                .defaultIfEmpty(false)
                .doFinally(signal -> restorePathVariables(request.exchange(), originalVariables));
    }

    /**
     * 读取 exchange 中已绑定的路径变量，探测前备份。
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> originalPathVariables(ServerWebExchange exchange) {
        return exchange.getAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    }

    /**
     * 探测结束后恢复原始路径变量，保证不污染调用方请求。
     */
    private void restorePathVariables(ServerWebExchange exchange, Map<String, String> original) {
        if (original == null) {
            exchange.getAttributes().remove(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        } else {
            exchange.getAttributes().put(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, original);
        }
    }

    /**
     * 在隔离的路径变量上下文中探测单个路由，并在结束时恢复原对象。
     *
     * <p>RouterFunction 匹配会把模板变量写入 exchange attributes。405 探测属于只读操作，
     * 不能删除或替换调用方已经放入原请求的属性。</p>
     */
    private Mono<Boolean> probeDelegate(RouterFunction<ServerResponse> delegate, ServerRequest request) {
        ServerWebExchange exchange = request.exchange();
        Map<String, Object> attributes = exchange.getAttributes();
        Set<String> originalKeys = new LinkedHashSet<>();
        Map<String, Object> originalValues = new java.util.LinkedHashMap<>();
        for (String key : ROUTING_ATTRIBUTE_KEYS) {
            if (attributes.containsKey(key)) {
                originalKeys.add(key);
                originalValues.put(key, attributes.get(key));
            }
            attributes.remove(key);
        }

        return delegate.route(request)
                .map(handler -> true)
                .defaultIfEmpty(false)
                .doOnNext(matched -> restoreRoutingAttributes(attributes, originalKeys, originalValues))
                .doOnError(error -> restoreRoutingAttributes(attributes, originalKeys, originalValues))
                .doOnCancel(() -> restoreRoutingAttributes(attributes, originalKeys, originalValues));
    }

    /** 恢复探测前的路径匹配属性，保持原属性值的对象身份不变。 */
    private void restoreRoutingAttributes(
            Map<String, Object> attributes, Set<String> originalKeys, Map<String, Object> originalValues) {
        for (String key : ROUTING_ATTRIBUTE_KEYS) {
            attributes.remove(key);
        }
        originalKeys.forEach(key -> attributes.put(key, originalValues.get(key)));
    }

    /**
     * 构建使用指定 HTTP 方法的变体请求，清除已绑定的路径变量。
     */
    private ServerRequest buildMutatedRequest(ServerRequest original, HttpMethod method) {
        ServerWebExchange mutatedExchange = original.exchange()
                .mutate()
                .request(builder -> builder.method(method))
                .build();
        return ServerRequest.create(mutatedExchange, original.messageReaders());
    }

    /**
     * 构建 405 Method Not Allowed 响应处理器。
     *
     * @param allowedMethods 该路径实际允许的 HTTP 方法集合，用于返回 {@code Allow} 响应头
     */
    private Mono<HandlerFunction<ServerResponse>> buildMethodNotAllowedHandler(Set<HttpMethod> allowedMethods) {
        return Mono.just(req -> ServerResponse.status(HttpStatus.METHOD_NOT_ALLOWED)
                .allow(allowedMethods.toArray(HttpMethod[]::new))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Result.error(405, "Method Not Allowed")));
    }

    /**
     * {@inheritDoc}
     *
     * @return 路由器的排序优先级（最低）
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * 探测使用的方法集合：覆盖所有常用 HTTP 方法，确保对任意方法注册的
     * 路由都能被其他方法的请求探测命中。
     */
    private static final List<HttpMethod> CANDIDATE_METHODS = List.of(
            HttpMethod.GET,
            HttpMethod.POST,
            HttpMethod.PUT,
            HttpMethod.PATCH,
            HttpMethod.DELETE,
            HttpMethod.HEAD,
            HttpMethod.OPTIONS);

    private static final List<String> ROUTING_ATTRIBUTE_KEYS =
            List.of(RouterFunctions.MATCHING_PATTERN_ATTRIBUTE, RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
}
