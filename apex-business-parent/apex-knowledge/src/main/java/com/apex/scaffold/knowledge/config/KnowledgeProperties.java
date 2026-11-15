package com.apex.scaffold.knowledge.config;

import java.time.Duration;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 知识库运行配置属性（{@code apex.knowledge.*}）。
 *
 * <p>三个能力各自独立开关，按能力级降级：索引未启用 → 检索返回空与摄取只入库不索引；
 * embedding 未启用 → 仅词法索引；chat 未启用 → 问答接口显式报错而检索可用。
 * 密钥类配置必须经环境变量注入，禁止写入版本库。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.knowledge")
@Validated
public class KnowledgeProperties {

    /** 摄取管线配置。 */
    private Ingest ingest = new Ingest();

    /** 对象存储配置。 */
    private Storage storage = new Storage();

    /** 文本向量化配置（OpenAI 兼容协议）。 */
    private Embedding embedding = new Embedding();

    /** 模型对话配置（OpenAI 兼容协议）。 */
    private Chat chat = new Chat();

    /** 检索配置。 */
    private Search search = new Search();

    /** Meilisearch 索引引擎配置（知识分块索引，独立于 system 六类资源索引）。 */
    private Meilisearch meilisearch = new Meilisearch();

    /** 清理与工作认领配置，独立于Meilisearch能力开关。 */
    private Cleanup cleanup = new Cleanup();

    /** 清理任务按租户顺序扫描，所有时长使用Spring Duration绑定（如20s/2m）。 */
    @Data
    public static class Cleanup {
        /** 是否自动扫描新删除与上传补偿，默认开启；禁用不等于清理完成。 */
        private boolean enabled = true;
        /** 扫描租户范围；空仅取专属任务表租户投影，不绕过共享拦截器扫描主档。 */
        private List<String> tenantIds = List.of();
        /** 每租户每轮最多认领数，默认50，仓储再限制至1000。 */
        private int batchSize = 50;
        /** 快速自动重试次数上限；DELETE tombstone长期复核不受此上限影响。 */
        private int maxAttempts = 10;
        /** UPLOAD/INGEST/CLEANUP工作租约，默认2分钟；过期owner不可继续写库。 */
        private Duration leaseDuration = Duration.ofMinutes(2);
        /** 活跃管线续租间隔，默认20秒，应明显短于租约。 */
        private Duration heartbeatInterval = Duration.ofSeconds(20);
        /** 首次失败重试等待，默认1分钟；乘尝试次数，最多60倍。 */
        private Duration retryDelay = Duration.ofMinutes(1);
        /** 长期tombstone定向复核周期，默认24小时；不物理删除凭证。 */
        private Duration tombstoneReviewInterval = Duration.ofHours(24);
        /** 同轮相邻文档最小等待，默认100毫秒，避免清理/摄取失败风暴。 */
        private Duration itemDelay = Duration.ofMillis(100);
        /** 历史softdeleted主档仅索引清理开关，默认关闭，不自动删历史object。 */
        private boolean legacyIndexEnabled = false;
        /** 历史索引清理必须显式配置租户范围，空范围不执行。 */
        private List<String> legacyTenantIds = List.of();
    }

    /** 摄取管线参数。 */
    @Data
    public static class Ingest {
        /** 分块字符上限（含）。 */
        private int chunkSize = 800;
        /** 相邻分块重叠字符数。 */
        private int chunkOverlap = 120;
        /** 单文件上传上限（MB）。 */
        private int maxUploadSizeMb = 50;
        /** 允许上传解析的文件扩展名（小写，不含点）。 */
        private List<String> supportedTypes =
                List.of("txt", "md", "pdf", "docx", "doc", "xlsx", "pptx", "html", "htm", "csv");
        /** 解析正文长度上限（字符），超出截断并标记。 */
        private int maxContentChars = 400_000;
    }

    /** 对象存储参数。 */
    @Data
    public static class Storage {
        /** 知识文档对象桶名。 */
        private String bucket = "knowledge";
    }

    /** 向量化参数。 */
    @Data
    public static class Embedding {
        /** 是否启用向量化（关闭时仅词法索引与检索）。 */
        private boolean enabled = false;
        /** OpenAI 兼容基址（如 https://dashscope.aliyuncs.com/compatible-mode/v1）。 */
        private String baseUrl = "";
        /** API Key（敏感，环境变量注入）。 */
        private String apiKey = "";
        /** Embedding 模型名。 */
        private String model = "";
        /** 向量维度（须与模型输出一致，索引 embedder 配置同源）。 */
        private int dimensions = 1024;
        /** 单批最大文本条数。 */
        private int batchSize = 32;
        /** 单次请求超时。 */
        private Duration timeout = Duration.ofSeconds(30);
    }

    /** 对话参数。 */
    @Data
    public static class Chat {
        /** 是否启用问答（关闭时问答接口显式报错）。 */
        private boolean enabled = false;
        /** OpenAI 兼容基址。 */
        private String baseUrl = "";
        /** API Key（敏感，环境变量注入）。 */
        private String apiKey = "";
        /** 对话模型名。 */
        private String model = "";
        /** 单次请求超时。 */
        private Duration timeout = Duration.ofSeconds(60);
        /** 携带的历史消息轮数（user+assistant 合计条数上限）。 */
        private int historySize = 6;
        /** 检索为空时的策略：fallback=模板兜底回复；answer=仍调用模型回答。 */
        private String emptyContextStrategy = "fallback";
        /** 检索上下文最大拼接字符数。 */
        private int maxContextChars = 4000;
    }

    /** 检索参数。 */
    @Data
    public static class Search {
        /** hybrid 语义占比（0-1，仅启用 embedding 时生效）。 */
        private double semanticRatio = 0.6;
        /** 问答引用条数（topK）。 */
        private int topK = 5;
    }

    /** Meilisearch 参数。 */
    @Data
    public static class Meilisearch {
        /** 是否启用知识索引引擎（关闭时检索返回空、摄取只入库）。 */
        private boolean enabled = false;
        /** Meilisearch 服务地址。 */
        private String url = "http://localhost:7700";
        /** Master API Key（敏感，环境变量注入）。 */
        private String apiKey = "";
        /** 索引名前缀。 */
        private String indexPrefix = "apex_";
        /** 批量写入条数。 */
        private int batchSize = 100;
        /** task轮询间隔，默认200毫秒。 */
        private Duration taskPollInterval = Duration.ofMillis(200);
        /** 每任务最多轮询150次，达到上限拒绝成功。 */
        private int taskMaxPolls = 150;
        /** 任务确认总等待上限，默认30秒，超时保持未知而非完成。 */
        private Duration taskTimeout = Duration.ofSeconds(30);
        /** 单个Meilisearch HTTP请求超时，默认10秒。 */
        private Duration requestTimeout = Duration.ofSeconds(10);
    }
}
