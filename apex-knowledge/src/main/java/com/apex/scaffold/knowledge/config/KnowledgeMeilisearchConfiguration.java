package com.apex.scaffold.knowledge.config;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 知识库 Meilisearch 客户端装配。
 *
 * <p>与 system 六类资源索引共存的命名空间隔离：知识分块使用独立索引
 * {@code indexPrefix + kb_chunks}，配置前缀独立为 {@code apex.knowledge.meilisearch.*}，
 * 不复用 {@code meilisearch.*}，避免与 {@code MeilisearchConfig} 的客户端 Bean 在
 * 组合部署（boot-all）场景下产生同类型冲突。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "apex.knowledge.meilisearch", name = "enabled", havingValue = "true")
public class KnowledgeMeilisearchConfiguration {

    /**
     * 知识库 Meilisearch SDK 客户端（线程安全单例；首次调用时才建立连接）。
     *
     * @param properties 知识库配置
     * @return Meilisearch 客户端
     */
    @Bean
    public Client knowledgeMeilisearchClient(KnowledgeProperties properties) {
        KnowledgeProperties.Meilisearch meili = properties.getMeilisearch();
        log.info("知识库 Meilisearch 客户端已装配: url={}, indexPrefix={}", meili.getUrl(), meili.getIndexPrefix());
        return new Client(new Config(meili.getUrl(), meili.getApiKey()));
    }
}
