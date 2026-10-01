package com.apex.scaffold.infrastructure.config;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Meilisearch 全文搜索引擎配置类
 *
 * <p>本配置类负责装配 Meilisearch Java 客户端，用于系统管理数据（用户、角色、菜单、部门、字典、配置）的全文索引与检索。
 *
 * <h3>技术选型理由</h3>
 * <ul>
 *   <li><b>CJK 支持好</b>：对中文、日文、韩文的分词与搜索支持优秀</li>
 *   <li><b>轻量高效</b>：内存占用比 Elasticsearch 低 80%，适合中小规模数据集</li>
 *   <li><b>官方 Java SDK</b>：提供类型安全的 Java 客户端，API 设计简洁</li>
 *   <li><b>部署简单</b>：单节点部署即可使用，无需复杂集群配置</li>
 * </ul>
 *
 * <h3>启用条件</h3>
 * <p>当配置 {@code meilisearch.enabled=true} 时生效，默认关闭。
 * 开发环境可在 {@code application-dev.yml} 中启用，生产环境通过环境变量 {@code MEILISEARCH_ENABLED} 控制。
 *
 * <h3>配置项说明</h3>
 * <pre>
 * meilisearch:
 *   enabled: true                      # 总开关
 *   url: http://localhost:7700         # Meilisearch 服务地址
 *   api-key: masterKey                 # Master API Key（生产必须注入随机密钥）
 *   index-prefix: apex_                # 索引名称前缀（多应用共用时隔离）
 *   batch-size: 100                    # 批量索引批次大小
 * </pre>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class SearchService {
 *
 *     private final Client meilisearchClient;
 *
 *     public void indexDocument() {
 *         Index index = meilisearchClient.index("apex_system_resources");
 *         index.addDocuments("[{\"id\":\"user_1\",\"title\":\"张三\"}]");
 *     }
 * }
 * }</pre>
 *
 * @author Apex Team
 * @since 1.3.0
 * @see Client Meilisearch Java 客户端
 * @see MeilisearchProperties 配置属性类
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "meilisearch", name = "enabled", havingValue = "true")
public class MeilisearchConfig {

    /**
     * 创建 Meilisearch 配置属性 Bean
     *
     * <p>从 {@code meilisearch.*} 配置项绑定属性，并启用 JSR-303 校验。
     *
     * @return Meilisearch 配置属性实例
     */
    @Bean
    @ConfigurationProperties(prefix = "meilisearch")
    @Validated
    public MeilisearchProperties meilisearchProperties() {
        return new MeilisearchProperties();
    }

    /**
     * 创建 Meilisearch 客户端 Bean
     *
     * <p>使用配置的 URL 和 API Key 初始化 Meilisearch Java SDK 客户端。
     * 客户端为线程安全单例，可注入到任意 Service 或 Repository 中使用。
     *
     * <h4>连接验证</h4>
     * <p>Bean 初始化时仅创建客户端对象，不会立即连接 Meilisearch 服务。
     * 首次调用索引操作时才建立连接，连接失败会抛出 {@link com.meilisearch.sdk.exceptions.MeilisearchException}。
     *
     * <h4>重试策略</h4>
     * <p>SDK 内部对网络错误、超时等临时故障会自动重试。
     * 业务代码应捕获异常并决定是否降级处理（如返回空结果、使用数据库 LIKE 查询等）。
     *
     * @param properties Meilisearch 配置属性
     * @return Meilisearch 客户端实例（单例、线程安全）
     * @throws IllegalArgumentException 当 URL 格式非法时抛出
     */
    @Bean
    public Client meilisearchClient(MeilisearchProperties properties) {
        Config config = new Config(properties.getUrl(), properties.getApiKey());
        Client client = new Client(config);
        log.info(
                "Meilisearch client initialized: url={}, indexPrefix={}",
                properties.getUrl(),
                properties.getIndexPrefix());
        return client;
    }

    /**
     * Meilisearch 配置属性类
     *
     * <p>从 {@code meilisearch.*} 配置项绑定属性，支持环境变量覆盖。
     *
     * <h3>安全注意事项</h3>
     * <ul>
     *   <li><b>API Key 保护</b>：生产环境必须通过环境变量 {@code MEILISEARCH_API_KEY} 注入随机密钥，
     *       禁止在配置文件中硬编码</li>
     *   <li><b>网络隔离</b>：生产环境 Meilisearch 服务应部署在内网，仅应用服务器可访问</li>
     *   <li><b>权限控制</b>：Master Key 拥有所有权限，业务代码应使用 Search Key（只读）查询索引</li>
     * </ul>
     */
    @Data
    public static class MeilisearchProperties {

        /**
         * Meilisearch 服务地址
         *
         * <p>示例：{@code http://localhost:7700}（本地开发）、{@code http://meilisearch:7700}（Docker Compose）、
         * {@code http://meilisearch.default.svc.cluster.local:7700}（Kubernetes）
         *
         * <p>默认值：{@code http://localhost:7700}
         */
        @NotBlank(message = "Meilisearch URL 不能为空")
        private String url = "http://localhost:7700";

        /**
         * Meilisearch Master API Key
         *
         * <p><b>安全警告</b>：Master Key 拥有创建索引、修改配置、删除数据等所有权限，
         * 生产环境必须通过环境变量 {@code MEILISEARCH_API_KEY} 注入，禁止硬编码。
         *
         * <p>开发环境默认值：{@code masterKey}（Meilisearch 默认密钥）
         *
         * <p>生产环境建议使用 32 字符以上随机字符串，示例生成方式：
         * <pre>{@code openssl rand -base64 32}</pre>
         */
        private String apiKey;

        /**
         * 索引名称前缀
         *
         * <p>用于多应用共用同一 Meilisearch 实例时的命名空间隔离。
         * 例如前缀为 {@code apex_}，则实际索引名为 {@code apex_system_resources}。
         *
         * <p>默认值：{@code apex_}
         */
        private String indexPrefix = "apex_";

        /**
         * 批量索引批次大小
         *
         * <p>批量添加/更新文档时，每批次包含的文档数量。
         * 建议范围：50-200，过大会增加单次请求内存占用，过小会增加网络往返次数。
         *
         * <p>默认值：{@code 100}
         */
        private int batchSize = 100;

        /**
         * 是否启用 Meilisearch
         *
         * <p>总开关，由 {@code @ConditionalOnProperty} 读取。
         * 开发环境在 {@code application-dev.yml} 中设为 {@code true}，
         * 生产环境通过环境变量 {@code MEILISEARCH_ENABLED} 控制。
         *
         * <p>默认值：{@code false}（关闭）
         */
        private boolean enabled = false;
    }
}
