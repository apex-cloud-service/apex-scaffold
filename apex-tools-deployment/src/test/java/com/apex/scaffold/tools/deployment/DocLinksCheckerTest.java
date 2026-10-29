package com.apex.scaffold.tools.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link DocLinksChecker} 的端到端行为断言：在临时仓库内构造各类链接场景，
 * 校验输出原因、统计行与退出码与原 Python 实现契约一致。
 *
 * @author Apex Team
 * @since 1.3.1
 */
class DocLinksCheckerTest {

    @TempDir
    Path root;

    /** 将 root 下指定相对路径文件注册进 NUL 分隔清单并执行检查，返回 [退出码, 输出]。 */
    private Result scan(String... files) throws IOException, InterruptedException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int code = DocLinksChecker.check(filesList(files), root, new PrintStream(buffer, true, StandardCharsets.UTF_8));
        return new Result(code, buffer.toString(StandardCharsets.UTF_8));
    }

    private Path filesList(String... files) throws IOException {
        Path list = Files.createTempFile("doc-links-test", ".list");
        StringBuilder nul = new StringBuilder();
        for (String file : files) {
            nul.append(root.resolve(file).toAbsolutePath()).append('\0');
        }
        Files.writeString(list, nul.toString(), StandardCharsets.UTF_8);
        return list;
    }

    private void write(String path, String content) throws IOException {
        Path target = root.resolve(path);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    @Test
    void validLinksAndAnchorsPass() throws Exception {
        write("docs/guide.md", "# 安装指南\n\n<a id=\"custom\"></a>\n\n参见 [总览](index.md)。\n");
        write("docs/index.md", "# 总览\n\n[标题锚点](guide.md#安装指南) [显式锚点](guide.md#custom)\n");
        Result result = scan("docs/guide.md", "docs/index.md");
        assertEquals(0, result.code(), result.output);
        assertTrue(result.output.contains("扫描完成"), result.output);
        assertTrue(result.output.contains("   总链接数: 3"), result.output);
        assertTrue(result.output.contains("   断裂链接: 0"), result.output);
    }

    @Test
    void missingPathIsReported() throws Exception {
        write("docs/a.md", "[缺失](nope.md)\n");
        Result result = scan("docs/a.md");
        assertEquals(1, result.code());
        assertTrue(result.output.contains("坏链接（路径不存在）"), result.output);
        assertTrue(result.output.contains("   断裂链接: 1"), result.output);
    }

    @Test
    void caseMismatchIsReported() throws Exception {
        write("docs/READ.md", "# 大写\n");
        write("docs/linker.md", "[小写](read.md)\n");
        Result result = scan("docs/READ.md", "docs/linker.md");
        assertEquals(1, result.code());
        // 大小写不敏感文件系统上表现为大小写不匹配，敏感文件系统上表现为路径不存在
        assertTrue(result.output.contains("坏链接（大小写不匹配）") || result.output.contains("坏链接（路径不存在）"), result.output);
    }

    @Test
    void missingAnchorIsReported() throws Exception {
        write("docs/t.md", "# 标题\n\n[跳转](t.md#不存在)\n");
        Result result = scan("docs/t.md");
        assertEquals(1, result.code());
        assertTrue(result.output.contains("坏链接（锚点不存在）"), result.output);
    }

    @Test
    void duplicateHeadingSuffixAnchors() throws Exception {
        write("docs/dup.md", "# 重复\n\n# 重复\n\n[第二份](dup.md#重复-1)\n");
        Result result = scan("docs/dup.md");
        assertEquals(0, result.code(), result.output);
    }

    @Test
    void linksInsideCodeAndCommentsAreIgnored() throws Exception {
        write(
                "docs/c.md",
                """
                ```bash
                echo "[假链接](gone.md)"
                ```

                <!-- [注释链接](gone.md) -->

                `行内 [假链接](gone.md)`

                [真实](c.md#安装)
                """);
        Result result = scan("docs/c.md");
        assertEquals(1, result.code(), result.output);
        assertTrue(result.output.contains("   总链接数: 1"), result.output);
        assertTrue(result.output.contains("坏链接（锚点不存在）"), result.output);
    }

    @Test
    void externalSchemesAndFragmentsSkipped() throws Exception {
        write("docs/e.md", "[官网](https://example.com/a?b=c#d) [邮件](mailto:a@b.c) [本页](#标题)\n" + "# 标题\n");
        Result result = scan("docs/e.md");
        assertEquals(0, result.code(), result.output);
        assertTrue(result.output.contains("   总链接数: 1"), result.output);
    }

    @Test
    void referenceDefinitionsResolved() throws Exception {
        write("docs/r.md", "[仓库][repo] 与 [折叠][]\n\n[repo]: r.md#标题\n[折叠]: r.md\n\n# 标题\n");
        Result result = scan("docs/r.md");
        assertEquals(0, result.code(), result.output);
        assertTrue(result.output.contains("   总链接数: 2"), result.output);
    }

    @Test
    void directoryLinkUsesReadmeAnchors() throws Exception {
        write("pkg/README.md", "# 包\n\n## 成员\n");
        write("docs/d.md", "[目录](../pkg#成员) [缺失](../pkg#不存在)\n");
        Result result = scan("docs/d.md");
        assertEquals(1, result.code());
        assertTrue(result.output.contains("坏链接（锚点不存在）"), result.output);
    }

    @Test
    void percentEncodedAndHtmlEntityTargets() throws Exception {
        write("docs/空 格.md", "# 空格\n");
        write("docs/p.md", "[百分号](%E7%A9%BA%20%E6%A0%BC.md) [片段](%E7%A9%BA%20%E6%A0%BC.md#空格)\n");
        Result result = scan("docs/空 格.md", "docs/p.md");
        assertEquals(0, result.code(), result.output);
    }

    @Test
    void gitignoreSkippedWithoutGitRepo() throws Exception {
        write("docs/g.md", "[生成物](../generated/out.md)\n");
        write("generated/out.md", "# 生成\n");
        write(".gitignore", "generated/\n");
        // 无 .git 目录时整体跳过 gitignore 检查（与原实现一致）
        Result result = scan("docs/g.md");
        assertEquals(0, result.code(), result.output);
    }

    @Test
    void gitignoreCheckedWhenGitRepoPresent() throws Exception {
        write("docs/g.md", "[生成物](../generated/out.md)\n");
        write("generated/out.md", "# 生成\n");
        write(".gitignore", "generated/\n");
        Process init = new ProcessBuilder("git", "init", "-q", root.toString()).start();
        assumeGitAvailable(init);
        Result result = scan("docs/g.md");
        assertEquals(1, result.code());
        assertTrue(result.output.contains("坏链接（目标被 .gitignore 排除）"), result.output);
    }

    private void assumeGitAvailable(Process init) throws InterruptedException {
        if (init.waitFor() != 0) {
            throw new org.opentest4j.TestAbortedException("git 不可用，跳过 gitignore 场景");
        }
    }

    @Test
    void fileAndUnsupportedSchemesSkipped() throws Exception {
        // file://localhost 计入链接数并按本机路径校验；其他 scheme 整体跳过
        write("docs/f.md", "[文件](file://localhost/etc/hosts) [其他](ftp://example.com/x.md)\n");
        Result result = scan("docs/f.md");
        assertEquals(0, result.code(), result.output);
        assertTrue(result.output.contains("   总链接数: 1"), result.output);
    }

    private record Result(int code, String output) {}
}
