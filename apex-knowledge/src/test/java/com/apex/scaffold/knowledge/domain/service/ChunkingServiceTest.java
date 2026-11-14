package com.apex.scaffold.knowledge.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.knowledge.domain.model.KnowledgeChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 滑动窗口分块服务单元测试。
 *
 * @author Apex Team
 * @since 1.3.2
 */
class ChunkingServiceTest {

    /**
     * 构建指定参数的分块服务。
     *
     * @param chunkSize 块上限
     * @param overlap 重叠
     * @return 分块服务
     */
    private ChunkingService service(int chunkSize, int overlap) {
        return new ChunkingService(chunkSize, overlap);
    }

    @Test
    void paragraphsArePackedIntoSingleChunkWhenSmall() {
        ChunkingService chunking = service(100, 10);
        List<KnowledgeChunk> chunks = chunking.chunk("第一段内容。\n\n第二段内容。\n\n第三段内容。");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getSeq()).isZero();
        assertThat(chunks.get(0).getContent()).contains("第一段", "第二段", "第三段");
    }

    @Test
    void oversizedParagraphIsHardSplitWithOverlap() {
        ChunkingService chunking = service(50, 10);
        String longText = "字".repeat(120);
        List<KnowledgeChunk> chunks = chunking.chunk(longText);
        assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
        assertThat(chunks.get(0).getContent()).hasSize(50);
        // 相邻块重叠 10 字符
        assertThat(chunks.get(1).getContent())
                .startsWith(chunks.get(0).getContent().substring(40));
    }

    @Test
    void blankTextYieldsNoChunks() {
        ChunkingService chunking = service(100, 10);
        assertThat(chunking.chunk("   \n\n  ")).isEmpty();
    }

    @Test
    void contentHashIsStableForIdenticalContent() {
        ChunkingService chunking = service(100, 10);
        List<KnowledgeChunk> chunks = chunking.chunk("相同内容");
        assertThat(chunks.get(0).getContentHash())
                .isEqualTo(chunking.chunk("相同内容").get(0).getContentHash())
                .hasSize(64);
    }

    @Test
    void invalidParametersAreRejected() {
        assertThatThrownBy(() -> service(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service(100, 100)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenEstimateIsCharacterBased() {
        ChunkingService chunking = service(100, 10);
        List<KnowledgeChunk> chunks = chunking.chunk("八字符内容");
        assertThat(chunks.get(0).getTokenEstimate()).isEqualTo(5 / 2);
    }
}
