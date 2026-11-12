package com.apex.scaffold.application.config;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 统一待办聚合门面配置（S4-a）。
 *
 * <p>{@code GET /api/v2/todo} 聚合工作流待办任务、office 待审批/未读站内信与
 * system 通知收件箱三个数据源所需的数据源地址、单次拉取条数与单源调用超时。
 * 默认地址指向本机各服务开发端口，部署时按拓扑覆盖：
 *
 * <ul>
 *   <li>{@code apex.todo.sources.workflow}：工作流服务基址
 *       （内部端点 {@code /internal/v1/workflow/todo/tasks}）</li>
 *   <li>{@code apex.todo.sources.office}：office 服务基址
 *       （内部端点 {@code /internal/v1/office/todo}）</li>
 *   <li>{@code apex.todo.page-size}：工作流待办单次拉取条数（默认 10）</li>
 *   <li>{@code apex.todo.approval-limit} / {@code apex.todo.message-limit}：
 *       office 待审批/未读站内信单次拉取上限（默认 10）</li>
 *   <li>{@code apex.todo.timeout-millis}：单源调用超时毫秒数（默认 3000）</li>
 *   <li>{@code apex.todo.bulkhead.<source>}：单源并发隔离舱壁的最大并发数；未配置的源
 *       使用 {@code apex.todo.default-bulkhead-max-concurrency}（默认 8）。饱和即该源
 *       立即降级（不排队等待），与概览门面的隔离语义一致</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.todo")
public class TodoProperties {

    /** 数据源名称 → 服务基址；键固定为 workflow/office。 */
    private Map<String, String> sources = new LinkedHashMap<>(Map.of(
            "workflow", "http://127.0.0.1:8093",
            "office", "http://127.0.0.1:8084"));

    /** 工作流待办任务单次拉取条数。 */
    private int pageSize = 10;

    /** office 待审批条目单次拉取上限。 */
    private int approvalLimit = 10;

    /** office 未读站内信单次拉取上限。 */
    private int messageLimit = 10;

    /** 单数据源调用超时毫秒数。 */
    private long timeoutMillis = 3000;

    /** 数据源名称 → 舱壁最大并发数；仅对门面实际拉取的源生效。 */
    private Map<String, Integer> bulkhead = new LinkedHashMap<>();

    /** 未显式配置舱壁并发数的源的默认最大并发数。 */
    private int defaultBulkheadMaxConcurrency = 8;
}
