package com.apex.scaffold.application.service;

import com.apex.scaffold.application.config.TodoProperties;
import com.apex.scaffold.application.dto.internal.UnifiedTodoResponse;
import com.apex.scaffold.application.dto.internal.UnifiedTodoResponse.SourceSection;
import com.apex.scaffold.application.dto.internal.UnifiedTodoResponse.TodoItem;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.contract.dto.internal.InternalTodoItem;
import com.apex.scaffold.contract.dto.internal.OfficeTodoInternalResponse;
import com.apex.scaffold.domain.model.entity.SystemMessageEntity;
import com.apex.scaffold.domain.repository.SystemMessageRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 统一待办聚合门面服务（S4-a）。
 *
 * <p>并行拉取工作流待办任务（HTTP）、office 待审批与未读站内信（HTTP）、
 * system 通知收件箱（进程内）三个数据源，聚合为当前用户的统一待办负载；
 * 任一数据源超时、不可达或返回业务错误时，该源降级为 {@code DEGRADED}
 * （结果缺失 + 错误摘要），不影响其他数据源（部分可用）。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>数据源地址走 {@code apex.todo.sources.*} 配置，默认指向本机各服务端口；
 *       notifications 源为 system 进程内仓储直调，无 HTTP 开销</li>
 *   <li>待办条目为用户个人数据：响应<b>不缓存、不合并并发请求</b>——这是概览门面
 *       “租户级数据按租户共享缓存”契约的反面（S2 显式契约），引入用户维度数据后
 *       任何租户/用户共享缓存都会造成越权读取，故每次请求实时聚合</li>
 *   <li>每个数据源带独立并发舱壁（JDK {@link java.util.concurrent.Semaphore}，与概览门面
 *       同一实现，不引入 Resilience4j 以保证 basic 档安全）：饱和即该源立即降级不排队；
 *       许可随订阅终止经 {@code doFinally} 释放，获取动作在 {@code Mono.defer} 内执行</li>
 *   <li>调用下游时透传调用方的 {@code Authorization} 头：workflow 与 office 内部待办端点
 *       均从验签 JWT 建立认证用户，无凭证时该源自然降级</li>
 *   <li>WebClient 为原生异步调用，不占用阻塞调度器</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Service
public class TodoFacadeService {

    private final TodoProperties properties;

    private final ObjectMapper objectMapper;

    private final WebClient webClient;

    private final SystemMessageRepository systemMessageRepository;

    /** 数据源名称 → 并发隔离舱壁；按需创建，许可数来自配置。 */
    private final ConcurrentHashMap<String, Semaphore> bulkheads = new ConcurrentHashMap<>();

    /** 下游分页负载的泛型反序列化引用（静态常量避免每次请求重建）。 */
    private static final TypeReference<PageResponse<InternalTodoItem>> PAGE_TYPE = new TypeReference<>() {};

    /**
     * 构造统一待办门面。
     *
     * @param properties              统一待办配置
     * @param objectMapper            JSON 序列化器
     * @param webClientBuilder        Boot 管理的客户端构建器
     * @param internalAuthProperties  内部认证配置
     * @param internalServiceSigner   内部签名器
     * @param systemMessageRepository system 通知收件箱仓储（进程内源）
     */
    public TodoFacadeService(
            TodoProperties properties,
            ObjectMapper objectMapper,
            WebClient.Builder webClientBuilder,
            com.apex.scaffold.common.internal.InternalAuthProperties internalAuthProperties,
            com.apex.scaffold.common.internal.InternalServiceSigner internalServiceSigner,
            SystemMessageRepository systemMessageRepository) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = com.apex.scaffold.common.internal.InternalAuthExchangeFilter.apply(
                        webClientBuilder, internalAuthProperties, internalServiceSigner)
                .build();
        this.systemMessageRepository = systemMessageRepository;
    }

    /**
     * 查询当前用户的跨域统一待办聚合。
     *
     * <p>不做缓存与并发合并（个人数据，见类注释）；单源降级不向上传播。
     *
     * @param userId        当前用户 ID（system 业务用户主键，用于进程内通知源）
     * @param authorization 调用方 Authorization 头（原样透传给需要认证的数据源，可为空）
     * @return 聚合响应（部分数据源降级时 {@code degraded=true}）
     */
    public Mono<UnifiedTodoResponse> getUnifiedTodo(Long userId, String authorization) {
        Mono<SourceSection> workflow = fetchSource(
                "workflow",
                buildUrl("workflow", "/internal/v1/workflow/todo/tasks?pageNum=1&pageSize=" + properties.getPageSize()),
                authorization,
                this::parseWorkflowBody);
        Mono<SourceSection> office = fetchSource(
                "office",
                buildUrl(
                        "office",
                        "/internal/v1/office/todo?approvalLimit=" + properties.getApprovalLimit() + "&messageLimit="
                                + properties.getMessageLimit()),
                authorization,
                this::parseOfficeBody);
        Mono<SourceSection> notifications = fetchNotifications(userId);
        return Mono.zip(workflow, office, notifications).map(tuple -> {
            Map<String, SourceSection> sources = new LinkedHashMap<>();
            sources.put("workflow", tuple.getT1());
            sources.put("office", tuple.getT2());
            sources.put("notifications", tuple.getT3());
            boolean degraded = sources.values().stream().anyMatch(source -> !"UP".equals(source.status()));
            return new UnifiedTodoResponse(userId, LocalDateTime.now(), degraded, sources);
        });
    }

    /**
     * 拉取单个数据源并映射为单源结果；任何失败均降级，不向上传播错误。
     *
     * <p>并发隔离语义与概览门面一致：订阅时先获取该源舱壁许可，饱和时立即降级不排队；
     * 许可随上游调用终止（完成/错误/超时）释放。
     *
     * @param name          数据源名称（日志与舱壁键）
     * @param url           完整端点地址；未配置时为 {@code null}
     * @param authorization 透传的 Authorization 头
     * @param parser        响应体解析函数（信封 + 负载 → 单源结果）
     * @return 单源结果（永不错误）
     */
    protected Mono<SourceSection> fetchSource(
            String name, String url, String authorization, Function<String, SourceSection> parser) {
        if (url == null) {
            return Mono.just(SourceSection.degraded("source not configured"));
        }
        Semaphore bulkhead = bulkheadFor(name);
        return Mono.defer(() -> {
            if (!bulkhead.tryAcquire()) {
                log.warn("统一待办数据源舱壁饱和: source={}", name);
                return Mono.just(SourceSection.degraded("bulkhead saturated"));
            }
            return fetchBody(url, authorization)
                    .timeout(Duration.ofMillis(properties.getTimeoutMillis()))
                    .map(parser)
                    .onErrorResume(error -> {
                        log.warn("统一待办数据源降级: source={}, error={}", name, error.toString());
                        return Mono.just(SourceSection.degraded(error.getClass().getSimpleName()));
                    })
                    .doFinally(signal -> bulkhead.release());
        });
    }

    /**
     * 进程内拉取当前用户未读通知；与其他源共用舱壁语义，失败降级不向上传播。
     *
     * @param userId 当前用户 ID
     * @return 单源结果（永不错误）
     */
    protected Mono<SourceSection> fetchNotifications(Long userId) {
        Semaphore bulkhead = bulkheadFor("notifications");
        return Mono.defer(() -> {
            if (!bulkhead.tryAcquire()) {
                log.warn("统一待办数据源舱壁饱和: source=notifications");
                return Mono.just(SourceSection.degraded("bulkhead saturated"));
            }
            return systemMessageRepository
                    .pageByUser(userId, 1, properties.getPageSize(), true)
                    .map(page -> SourceSection.up(
                            page.getTotal(),
                            page.getRecords().stream()
                                    .map(entity -> toTodoItem("notifications", "notification", entity))
                                    .toList()))
                    .onErrorResume(error -> {
                        log.warn("统一待办数据源降级: source=notifications, error={}", error.toString());
                        return Mono.just(SourceSection.degraded(error.getClass().getSimpleName()));
                    })
                    .doFinally(signal -> bulkhead.release());
        });
    }

    /**
     * 执行数据源 HTTP GET；调用方的 {@code Authorization} 头非空时原样透传。
     *
     * <p>可见性为 {@code protected} 仅供测试覆写注入桩响应体；WebClient 原生异步，
     * 不占用阻塞调度器。
     *
     * @param url           完整端点地址
     * @param authorization 透传的 Authorization 头
     * @return 响应体字符串
     */
    protected Mono<String> fetchBody(String url, String authorization) {
        WebClient.RequestHeadersSpec<?> spec = webClient.get().uri(url).accept(MediaType.APPLICATION_JSON);
        if (authorization != null && !authorization.isBlank()) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return spec.retrieve().bodyToMono(String.class);
    }

    /**
     * 解析 workflow 待办分页负载：成功映射条目并补充来源标识，失败或解析异常降级。
     *
     * @param body 下游响应体
     * @return 单源结果
     */
    private SourceSection parseWorkflowBody(String body) {
        JsonNode data = parseEnvelope(body);
        if (data == null) {
            return SourceSection.degraded("invalid response body");
        }
        try {
            PageResponse<InternalTodoItem> page = objectMapper.readValue(data.toString(), PAGE_TYPE);
            List<TodoItem> items = page.getRecords().stream()
                    .map(item -> toTodoItem("workflow", item))
                    .toList();
            return SourceSection.up(page.getTotal(), items);
        } catch (Exception ex) {
            return SourceSection.degraded("invalid response body");
        }
    }

    /**
     * 解析 office 待办负载：合并待审批与未读消息两组条目，总数相加，失败降级。
     *
     * @param body 下游响应体
     * @return 单源结果
     */
    private SourceSection parseOfficeBody(String body) {
        JsonNode data = parseEnvelope(body);
        if (data == null) {
            return SourceSection.degraded("invalid response body");
        }
        try {
            OfficeTodoInternalResponse payload = objectMapper.treeToValue(data, OfficeTodoInternalResponse.class);
            List<TodoItem> items = new ArrayList<>();
            if (payload.getApprovals() != null) {
                payload.getApprovals().forEach(item -> items.add(toTodoItem("office", item)));
            }
            if (payload.getMessages() != null) {
                payload.getMessages().forEach(item -> items.add(toTodoItem("office", item)));
            }
            long total = (payload.getApprovalTotal() == null ? 0L : payload.getApprovalTotal())
                    + (payload.getUnreadTotal() == null ? 0L : payload.getUnreadTotal());
            return SourceSection.up(total, items);
        } catch (Exception ex) {
            return SourceSection.degraded("invalid response body");
        }
    }

    /**
     * 解析下游 {@code Result} 信封：成功取 {@code data} 节点，失败返回 {@code null}。
     *
     * @param body 下游响应体
     * @return data 节点或 {@code null}
     */
    private JsonNode parseEnvelope(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.path("code").asInt(-1) == 0) {
                return root.path("data");
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 契约条目 → 对外条目，补充来源标识。
     *
     * @param source 来源域标识
     * @param item   契约条目
     * @return 对外条目
     */
    private TodoItem toTodoItem(String source, InternalTodoItem item) {
        return new TodoItem(
                source,
                item.getItemType(),
                item.getTitle(),
                item.getLink(),
                item.getOccurredAt(),
                item.getStatus(),
                item.getRefId());
    }

    /**
     * 通知实体 → 对外条目。
     *
     * @param source 来源域标识
     * @param type   条目类型
     * @param entity 通知实体
     * @return 对外条目
     */
    private TodoItem toTodoItem(String source, String type, SystemMessageEntity entity) {
        return new TodoItem(
                source,
                type,
                entity.getContent(),
                null,
                entity.getCreateTime(),
                "UNREAD",
                entity.getId() == null ? null : String.valueOf(entity.getId()));
    }

    /**
     * 读取数据源的并发舱壁；按源名惰性创建，许可数取源级配置或全局默认值。
     *
     * @param name 数据源名称
     * @return 该源的舱壁信号量
     */
    private Semaphore bulkheadFor(String name) {
        return bulkheads.computeIfAbsent(
                name,
                source -> new Semaphore(
                        properties.getBulkhead().getOrDefault(source, properties.getDefaultBulkheadMaxConcurrency())));
    }

    /**
     * 拼接数据源完整地址；基址未配置时返回 {@code null}，避免 {@code null} 与路径
     * 字符串拼接成非空伪地址（那样会绕过 {@link #fetchSource} 的未配置降级分支）。
     *
     * @param name 数据源名称
     * @param path 端点路径（以 {@code /} 开头）
     * @return 完整地址或 {@code null}
     */
    private String buildUrl(String name, String path) {
        String base = properties.getSources().get(name);
        if (base == null || base.isBlank()) {
            return null;
        }
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path;
    }
}
