package com.apex.scaffold.knowledge.infrastructure.parse;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.gateway.DocumentParserPort;
import com.apex.scaffold.knowledge.domain.model.ParsedDocument;
import java.io.InputStream;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Apache Tika 文档解析适配器。
 *
 * <p>同步阻塞解析隔离到 {@link BlockingIoSchedulers#storage()}；正文超限时截断并在
 * 尾部追加截断标记，避免超大对象撑爆数据库行与索引请求。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Slf4j
@Component
public class TikaDocumentParser implements DocumentParserPort {

    private static final String TRUNCATION_MARK = "\n\n[内容超长已截断]";

    private final KnowledgeProperties properties;

    /**
     * 注入运行配置（正文截断上限）。
     *
     * @param properties 知识库配置
     */
    public TikaDocumentParser(KnowledgeProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<ParsedDocument> parse(String fileName, String contentType, Supplier<InputStream> content) {
        int maxChars = properties.getIngest().getMaxContentChars();
        return Mono.fromCallable(() -> {
                    // writeLimit 传入正文上限加截断标记余量；Tika 在达到上限时抛出
                    // WriteLimitReachedException，此处改为尽力保留已抽取部分。
                    BodyContentHandler handler = new BodyContentHandler(maxChars + TRUNCATION_MARK.length());
                    Metadata metadata = new Metadata();
                    metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName == null ? "unknown" : fileName);
                    if (contentType != null && !contentType.isBlank()) {
                        metadata.set(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE, contentType);
                    }
                    try (InputStream stream = content.get()) {
                        new AutoDetectParser().parse(stream, handler, metadata);
                    } catch (org.apache.tika.exception.WriteLimitReachedException e) {
                        log.info("文档解析正文超限已截断: fileName={}", fileName);
                        return new ParsedDocument(handler.toString() + TRUNCATION_MARK, contentType);
                    }
                    String text = handler.toString();
                    if (text.length() > maxChars) {
                        text = text.substring(0, maxChars) + TRUNCATION_MARK;
                    }
                    return new ParsedDocument(text, contentType);
                })
                .subscribeOn(BlockingIoSchedulers.storage());
    }
}
