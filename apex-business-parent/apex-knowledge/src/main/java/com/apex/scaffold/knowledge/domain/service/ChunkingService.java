package com.apex.scaffold.knowledge.domain.service;

import com.apex.scaffold.knowledge.domain.model.KnowledgeChunk;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 滑动窗口分块领域服务。
 *
 * <p>中文正文优先按空行段落聚包：段落不足一块时合并，超过块上限时按字符窗口
 * 硬切并保留重叠。token 数以「字符数 ÷ 2」估算（中文近似），仅作排序与展示参考，
 * 不承诺与具体分词器一致。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Component
public class ChunkingService {

    private static final String PARAGRAPH_SEPARATOR_PATTERN = "\\n{2,}";

    private final int chunkSize;

    private final int chunkOverlap;

    /**
     * 构建分块服务（分块参数来自运行配置）。
     *
     * @param chunkSize 块上限（字符数，含）
     * @param chunkOverlap 相邻块重叠字符数，必须小于块上限
     */
    public ChunkingService(
            @Value("${apex.knowledge.ingest.chunk-size:800}") int chunkSize,
            @Value("${apex.knowledge.ingest.chunk-overlap:120}") int chunkOverlap) {
        if (chunkSize <= 0 || chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("分块参数无效: chunkSize=" + chunkSize + ", overlap=" + chunkOverlap);
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    /**
     * 将纯文本切分为分块列表。
     *
     * @param text 已解析正文（非空）
     * @return 分块列表（seq 从 0 递增；正文全部为空白时返回空列表）
     */
    public List<KnowledgeChunk> chunk(String text) {
        List<String> segments = packParagraphs(normalize(text));
        List<KnowledgeChunk> chunks = new ArrayList<>(segments.size());
        int seq = 0;
        for (String content : segments) {
            String trimmed = content.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            chunks.add(KnowledgeChunk.builder()
                    .seq(seq++)
                    .content(trimmed)
                    .contentHash(sha256Hex(trimmed))
                    .tokenEstimate(trimmed.length() / 2)
                    .build());
        }
        return chunks;
    }

    /**
     * 规范化正文：统一换行、压缩连续空行并去除首尾空白。
     *
     * @param text 原始正文
     * @return 规范化正文
     */
    private String normalize(String text) {
        return text == null
                ? ""
                : text.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    /**
     * 按段落聚包并硬切超长段落。
     *
     * @param text 规范化正文
     * @return 依次排列的块文本
     */
    private List<String> packParagraphs(String text) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : text.split(PARAGRAPH_SEPARATOR_PATTERN)) {
            String p = paragraph.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() > chunkSize) {
                if (current.length() > 0) {
                    blocks.add(current.toString());
                    current.setLength(0);
                }
                blocks.addAll(hardSplit(p));
                continue;
            }
            if (current.length() > 0 && current.length() + p.length() + 1 > chunkSize) {
                blocks.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(p);
        }
        if (current.length() > 0) {
            blocks.add(current.toString());
        }
        return blocks;
    }

    /**
     * 按字符窗口硬切超长段落，保留 overlap 重叠。
     *
     * @param paragraph 超长段落
     * @return 切片列表
     */
    private List<String> hardSplit(String paragraph) {
        List<String> slices = new ArrayList<>();
        int step = chunkSize - chunkOverlap;
        for (int start = 0; start < paragraph.length(); start += step) {
            int end = Math.min(start + chunkSize, paragraph.length());
            slices.add(paragraph.substring(start, end));
            if (end == paragraph.length()) {
                break;
            }
        }
        return slices;
    }

    /**
     * 计算内容 SHA-256 十六进制摘要。
     *
     * @param content 分块正文
     * @return 十六进制摘要
     */
    private String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 摘要不可用", e);
        }
    }
}
