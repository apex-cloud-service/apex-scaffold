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
 * {@link DocSearchServer} 静态文件服务行为断言：文件类型、目录索引、回环监听与路径穿越防护。
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

    private record Response(int status, String contentType, String body) {}
}
