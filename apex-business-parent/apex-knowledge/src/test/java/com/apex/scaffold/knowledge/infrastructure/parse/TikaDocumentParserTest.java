package com.apex.scaffold.knowledge.infrastructure.parse;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Tika 文档解析适配器测试：纯文本抽取与超长截断。
 *
 * @author Apex Team
 * @since 1.3.2
 */
class TikaDocumentParserTest {

    /**
     * 构建解析器（正文上限可配）。
     *
     * @param maxChars 正文上限
     * @return 解析器
     */
    private TikaDocumentParser parser(int maxChars) {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getIngest().setMaxContentChars(maxChars);
        return new TikaDocumentParser(properties);
    }

    @Test
    void plainTextIsExtractedVerbatim() {
        byte[] bytes = "知识库解析测试正文".getBytes(StandardCharsets.UTF_8);
        StepVerifier.create(parser(1000).parse("说明.txt", "text/plain", () -> new ByteArrayInputStream(bytes)))
                .assertNext(parsed -> {
                    assertThat(parsed.text()).contains("知识库解析测试正文");
                    assertThat(parsed.detectedContentType()).isEqualTo("text/plain");
                })
                .verifyComplete();
    }

    @Test
    void htmlIsParsedToPlainContent() {
        byte[] bytes =
                "<html><body><p>标题内容</p><script>var x=1;</script></body></html>".getBytes(StandardCharsets.UTF_8);
        StepVerifier.create(parser(1000).parse("page.html", "text/html", () -> new ByteArrayInputStream(bytes)))
                .assertNext(parsed -> {
                    assertThat(parsed.text()).contains("标题内容");
                    assertThat(parsed.text()).doesNotContain("var x=1;");
                })
                .verifyComplete();
    }

    @Test
    void oversizedContentIsTruncatedWithMark() {
        byte[] bytes = "字".repeat(500).getBytes(StandardCharsets.UTF_8);
        StepVerifier.create(parser(100).parse("big.txt", "text/plain", () -> new ByteArrayInputStream(bytes)))
                .assertNext(parsed -> {
                    assertThat(parsed.text()).contains("内容超长已截断");
                    assertThat(parsed.text().length()).isLessThanOrEqualTo(150);
                })
                .verifyComplete();
    }

    @Test
    void streamIsClosedAfterParsing() throws Exception {
        TrackingStream stream = new TrackingStream("关闭验证".getBytes(StandardCharsets.UTF_8));
        parser(1000).parse("a.txt", "text/plain", () -> stream).block();
        assertThat(stream.closed).isTrue();
    }

    /** 关闭行为可观测的输入流。 */
    private static class TrackingStream extends ByteArrayInputStream {

        /** 是否已关闭。 */
        boolean closed;

        /**
         * 构造可追踪流。
         *
         * @param bytes 内容
         */
        TrackingStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() {
            // ByteArrayInputStream.close() 为空操作，仅记录关闭标记
            closed = true;
        }
    }
}
