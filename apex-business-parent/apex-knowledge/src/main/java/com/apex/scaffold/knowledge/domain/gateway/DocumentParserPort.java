package com.apex.scaffold.knowledge.domain.gateway;

import com.apex.scaffold.knowledge.domain.model.ParsedDocument;
import java.io.InputStream;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/**
 * 文档解析端口：从对象字节流抽取纯文本正文。
 *
 * <p>实现方负责将阻塞解析隔离到专用调度器；解析失败以 {@link reactor.core.publisher.Mono#error}
 * 传播，由摄取编排转换为 {@code parseStatus=FAILED}，不向调用方吞错。
 *
 * @author Apex Team
 * @since 1.3.2
 */
public interface DocumentParserPort {

    /**
     * 解析对象内容为纯文本。
     *
     * @param fileName 原始文件名（用于类型嗅探与日志，不含敏感信息）
     * @param contentType 对象存储记录的内容类型（可能为 application/octet-stream）
     * @param content 对象字节流供给器；实现方负责关闭流
     * @return 解析结果（正文文本与探测到的内容类型）
     */
    Mono<ParsedDocument> parse(String fileName, String contentType, Supplier<InputStream> content);
}
