package com.apex.scaffold.tools.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link DocSearchServer} 静态文件服务行为断言：文件类型、目录索引、回环监听与路径穿越防护，
 * 以及 Markdown 默认渲染为带 GitHub 风格标题 id 的 HTML、{@code ?raw} 保留源码。
 *
 * @author Apex Team
 * @since 1.3.1
 */
class DocSearchServerTest {

    @TempDir
    Path root;

    HttpServer server;

    int port;

    @BeforeEach
    void startServer() throws IOException {
        server = DocSearchServer.start(0, root);
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private Response get(String path, String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection)
                URI.create("http://127.0.0.1:" + port + path).toURL().openConnection();
        connection.setRequestMethod(method);
        int status = connection.getResponseCode();
        String contentType = connection.getHeaderField("Content-Type");
        java.io.InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String body = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        connection.disconnect();
        return new Response(status, contentType, body);
    }

    @Test
    void servesFilesWithGuessedContentType() throws IOException {
        Files.writeString(root.resolve("doc.html"), "<html></html>", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("data.json"), "{}", StandardCharsets.UTF_8);
        Response html = get("/doc.html", "GET");
        assertEquals(200, html.status());
        assertEquals("text/html; charset=utf-8", html.contentType());
        Response json = get("/data.json", "GET");
        assertEquals("application/json; charset=utf-8", json.contentType());
    }

    @Test
    void headRequestHasNoBody() throws IOException {
        Files.writeString(root.resolve("a.md"), "# 标题", StandardCharsets.UTF_8);
        Response head = get("/a.md", "HEAD");
        assertEquals(200, head.status());
        assertEquals("", head.body());
    }

    @Test
    void missingFileReturns404() throws IOException {
        assertEquals(404, get("/absent.md", "GET").status());
    }

    @Test
    void directoryServesIndexHtmlFirst() throws IOException {
        Files.createDirectories(root.resolve("docs"));
        Files.writeString(root.resolve("docs/index.html"), "<p>索引页</p>", StandardCharsets.UTF_8);
        Response response = get("/docs/", "GET");
        assertEquals(200, response.status());
        assertTrue(response.body().contains("索引页"));
    }

    @Test
    void directoryWithoutIndexListsEntries() throws IOException {
        Files.createDirectories(root.resolve("docs"));
        Files.writeString(root.resolve("docs/note.md"), "n", StandardCharsets.UTF_8);
        Response response = get("/docs/", "GET");
        assertEquals(200, response.status());
        assertTrue(response.body().contains("note.md"), response.body());
    }

    @Test
    void traversalStaysInsideRoot() throws IOException {
        // 请求 /../../etc/passwd 会被剥离 .. 分量，最终映射到 root/etc/passwd（不存在）
        assertEquals(404, get("/../../etc/passwd", "GET").status());
    }

    @Test
    void percentDecodedPathsResolved() throws IOException {
        Files.writeString(root.resolve("空 格.md"), "内容", StandardCharsets.UTF_8);
        Response response = get("/%E7%A9%BA%20%E6%A0%BC.md", "GET");
        assertEquals(200, response.status());
        assertTrue(response.body().contains("内容"));
    }

    @Test
    void rendersMarkdownWithClickableTocAnchors() throws IOException {
        Files.writeString(
                root.resolve("guide.md"),
                """
                ## 目录

                1. [认证与授权](#1-认证与授权)
                2. [重复标题](#重复标题)
                3. [带格式标题](#3-带格式标题internalv1)

                <a id="1-认证与授权"></a>

                ## 1. 认证与授权

                正文，包含 **强调**、`code` 与表格：

                | 列 | 值 |
                | --- | --- |
                | a | b |

                ## 重复标题

                第二个同名标题。

                ## 重复标题

                第二个同名标题再次出现，用于验证序号后缀。

                ## 3. 带格式标题（/internal/v1/**）
                """,
                StandardCharsets.UTF_8);
        Response response = get("/guide.md", "GET");
        assertEquals(200, response.status());
        assertEquals("text/html; charset=utf-8", response.contentType());
        // 目录链接原样保留
        assertTrue(response.body().contains("href=\"#1-认证与授权\""), response.body());
        // 显式 <a id> 锚点透传
        assertTrue(response.body().contains("<a id=\"1-认证与授权\"></a>"), response.body());
        // GitHub slug 注入（含标点剥离与重复标题序号）
        assertTrue(response.body().contains("<h2 id=\"1-认证与授权\">"), response.body());
        assertTrue(response.body().contains("<h2 id=\"重复标题\">"), response.body());
        assertTrue(response.body().contains("<h2 id=\"重复标题-1\">"), response.body());
        assertTrue(response.body().contains("<h2 id=\"3-带格式标题internalv1\">"), response.body());
        // GFM 表格渲染
        assertTrue(response.body().contains("<table>"), response.body());
        assertTrue(response.body().contains("<td>a</td>"), response.body());
        // 硬性保证：页面内每个锚点链接都有同名 id 目标，点击必然可跳转
        var hrefs = new java.util.HashSet<String>();
        var matcher = java.util.regex.Pattern.compile("href=\"#([^\"]+)\"").matcher(response.body());
        while (matcher.find()) {
            hrefs.add(matcher.group(1));
        }
        var ids = new java.util.HashSet<String>();
        var idMatcher = java.util.regex.Pattern.compile("\\sid=\"([^\"]+)\"").matcher(response.body());
        while (idMatcher.find()) {
            ids.add(idMatcher.group(1));
        }
        assertTrue(ids.containsAll(hrefs), () -> "缺少跳转目标: " + new java.util.HashSet<>(hrefs) + " - " + ids);
    }

    @Test
    void rawQueryServesMarkdownSource() throws IOException {
        Files.writeString(root.resolve("a.md"), "# 标题\n\n正文段落", StandardCharsets.UTF_8);
        Response raw = get("/a.md?raw=1", "GET");
        assertEquals(200, raw.status());
        assertEquals("text/plain; charset=utf-8", raw.contentType());
        assertEquals("# 标题\n\n正文段落", raw.body());
        Response head = get("/a.md?raw", "HEAD");
        assertEquals(200, head.status());
    }

    private record Response(int status, String contentType, String body) {}
}
